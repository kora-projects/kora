package io.koraframework.http.server.common.request.mapper;

import io.koraframework.http.common.form.FormUrlEncoded;
import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.response.HttpServerResponseException;
import io.koraframework.http.server.common.request.HttpServerRequestMapper;

import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class FormUrlEncodedServerRequestMapper implements HttpServerRequestMapper<FormUrlEncoded> {

    @Override
    public FormUrlEncoded apply(HttpServerRequest request) throws IOException {
        var contentType = request.headers().getFirst("content-type");
        if (contentType == null || !contentType.equalsIgnoreCase("application/x-www-form-urlencoded")) {
            var rs = HttpServerResponseException.of(415, "Expected content type: 'application/x-www-form-urlencoded'");
            try {
                request.body().close();
            } catch (IOException e) {
                rs.addSuppressed(e);
            }
            throw rs;
        }
        try (var body = request.body()) {
            var full = body.getFullContentIfAvailable();
            if (full != null) {
                var str = StandardCharsets.UTF_8.decode(full).toString();
                var parts = FormUrlEncodedServerRequestMapper.read(str);
                return new FormUrlEncoded(parts);
            }
            try (var is = body.asInputStream()) {
                var bytes = is.readAllBytes();
                var str = new String(bytes, StandardCharsets.UTF_8);
                var parts = FormUrlEncodedServerRequestMapper.read(str);
                return new FormUrlEncoded(parts);
            }
        }
    }

    /**
     * <b>Русский</b>: Читает поле, значения которого соединены разделителем: тело делится по разделителю до декодирования, поэтому разделитель внутри значения не теряется
     * <hr>
     * <b>English</b>: Reads a field whose values are joined by the delimiter: the body is split by the delimiter before decoding, so a delimiter inside a value is kept
     * <br>
     * <br>
     * Пример / Example: <code>readDelimited("ids=1,2", "ids", ",")</code> -> <code>["1", "2"]</code>
     *
     * @return values of the field, or {@code null} when the body has no such field
     */
    @Nullable
    public static List<String> readDelimited(String body, String name, String delimiter) {
        // a space can't be sent as is, and `+` is a space of a value
        var rawDelimiter = " ".equals(delimiter) ? "%20" : delimiter;
        List<String> values = null;
        for (var s : body.split("&")) {
            if (s.isBlank()) {
                continue;
            }
            var valueStart = s.indexOf('=');
            var rawName = valueStart < 0 ? s : s.substring(0, valueStart);
            if (!URLDecoder.decode(rawName.trim(), StandardCharsets.UTF_8).equals(name)) {
                continue;
            }
            if (values == null) {
                values = new ArrayList<>();
            }
            var rawValue = valueStart < 0 ? "" : s.substring(valueStart + 1).trim();
            if (rawValue.isEmpty()) {
                continue;
            }
            if (rawValue.contains(rawDelimiter)) {
                for (var rawItem : rawValue.split(Pattern.quote(rawDelimiter), -1)) {
                    values.add(URLDecoder.decode(rawItem, StandardCharsets.UTF_8));
                }
            } else {
                // a client that encodes the delimiter along with the values
                values.addAll(List.of(URLDecoder.decode(rawValue, StandardCharsets.UTF_8).split(Pattern.quote(delimiter), -1)));
            }
        }
        return values;
    }

    public static Map<String, FormUrlEncoded.FormPart> read(String body) {
        var parts = new HashMap<String, FormUrlEncoded.FormPart>();
        for (var s : body.split("&")) {
            if (s.isBlank()) {
                continue;
            }
            var pair = s.split("=");
            var name = URLDecoder.decode(pair[0].trim(), StandardCharsets.UTF_8);
            var part = parts.computeIfAbsent(name, n -> new FormUrlEncoded.FormPart(n, new ArrayList<>()));
            if (pair.length > 1) {
                var value = URLDecoder.decode(pair[1].trim(), StandardCharsets.UTF_8);
                part.values().add(value);
            }
        }
        return parts;
    }

}
