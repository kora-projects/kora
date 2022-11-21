package ru.tinkoff.kora.http.server.common.form;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.util.ReactorUtils;
import ru.tinkoff.kora.http.common.form.FormUrlEncoded;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponseException;
import ru.tinkoff.kora.http.server.common.handler.HttpServerRequestMapper;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class FormUrlEncodedServerRequestMapper implements HttpServerRequestMapper<FormUrlEncoded> {
    @Override
    public Mono<FormUrlEncoded> apply(HttpServerRequest request) {
        var contentType = request.headers().getFirst("content-type");
        if (contentType == null || !contentType.equalsIgnoreCase("application/x-www-form-urlencoded")) {
            request.body().subscribe();
            return Mono.error(HttpServerResponseException.of(415, "Expected content type: 'application/x-www-form-urlencoded'"));
        }
        return ReactorUtils.toByteArrayMono(request.body()).map(bytes -> {
            var str = new String(bytes, StandardCharsets.UTF_8);
            var parts = FormUrlEncodedServerRequestMapper.read(str);
            return new FormUrlEncoded(parts);
        });
    }

    public static Map<String, FormUrlEncoded.FormPart> read(String body) {
        var parts = new HashMap<String, FormUrlEncoded.FormPart>();
        for (var s : body.split("&")) {
            if (s.isBlank()) {
                continue;
            }
            var pair = s.split("=");
            var name = URLDecoder.decode(pair[0].trim(), StandardCharsets.UTF_8);
            var value = URLDecoder.decode(pair[1].trim(), StandardCharsets.UTF_8);
            var part = parts.computeIfAbsent(name, n -> new FormUrlEncoded.FormPart(n, new ArrayList<>()));
            part.values().add(value);
        }
        return parts;
    }

}
