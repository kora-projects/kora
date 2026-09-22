package io.koraframework.http.server.common.telemetry.impl;

import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.response.HttpServerResponse;
import io.koraframework.logging.common.masking.raw.DataMasker;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;

public class DefaultHttpServerBodyConverter {

    private final Map<String, DataMasker> dataMaskers;

    public DefaultHttpServerBodyConverter() {
        this(List.of());
    }

    public DefaultHttpServerBodyConverter(List<DataMasker> dataMaskers) {
        this.dataMaskers = new HashMap<>();
        for (var dataMasker : dataMaskers) {
            this.dataMaskers.put(dataMasker.format(), dataMasker);
        }
    }

    @Nullable
    public String convertRequestBody(HttpServerRequest request, ByteBuffer body, @Nullable String contentType) {
        return convertBody(body, contentType, this.selectRequestDataMasker(request, contentType));
    }

    @Nullable
    public String convertResponseBody(HttpServerRequest request,
                                      HttpServerResponse response,
                                      ByteBuffer body,
                                      @Nullable String contentType) {
        return convertBody(body, contentType, this.selectResponseDataMasker(request, response, contentType));
    }

    @Nullable
    protected String convertBody(ByteBuffer body, @Nullable String contentType, @Nullable DataMasker dataMasker) {
        var charset = detectCharset(contentType);
        if (charset == null) {
            return null;
        }

        if (dataMasker != null) {
            var content = new byte[body.remaining()];
            body.get(content);
            return dataMasker.mask(content, charset);
        }

        try {
            return charset
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
                .replaceWith("?")
                .decode(body)
                .toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    @Nullable
    protected DataMasker selectRequestDataMasker(HttpServerRequest request, @Nullable String contentType) {
        return this.selectDataMasker(contentType);
    }

    @Nullable
    protected DataMasker selectResponseDataMasker(HttpServerRequest request,
                                                  HttpServerResponse response,
                                                  @Nullable String contentType) {
        return this.selectDataMasker(contentType);
    }

    @Nullable
    protected DataMasker selectDataMasker(@Nullable String contentType) {
        return this.dataMaskers.get(detectFormat(contentType));
    }

    @Nullable
    protected Charset detectCharset(String contentType) {
        if (contentType == null) {
            return null;
        }

        contentType = contentType.toLowerCase(Locale.ROOT);

        var split = contentType.split("; ?charset=", 2);
        if (split.length == 2) {
            try {
                var charsetName = split[1].strip();
                int end = charsetName.indexOf(';');
                if (end != -1) {
                    charsetName = charsetName.substring(0, end);
                }
                if (charsetName.startsWith("\"") && charsetName.endsWith("\"")) {
                    charsetName = charsetName.substring(1, charsetName.length() - 1);
                }

                return Charset.forName(charsetName);
            } catch (Exception e) {
                return null;
            }
        }

        var mimeType = split[0].strip();
        if (mimeType.startsWith("text/")
            || mimeType.endsWith("/json")
            || mimeType.endsWith("+json")
            || mimeType.endsWith("/xml")
            || mimeType.endsWith("+xml")) {
            return StandardCharsets.UTF_8;
        }

        if (mimeType.contains("application/x-www-form-urlencoded")) {
            return StandardCharsets.US_ASCII;
        }
        return null;
    }

    @Nullable
    protected String detectFormat(@Nullable String contentType) {
        if (contentType == null) {
            return null;
        }
        var mimeType = contentType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
        if (mimeType.equals("application/x-www-form-urlencoded")) {
            return "form-urlencoded";
        }
        if (mimeType.endsWith("/json") || mimeType.endsWith("+json")) {
            return "json";
        }
        if (mimeType.endsWith("/xml") || mimeType.endsWith("+xml")) {
            return "xml";
        }
        return null;
    }
}
