package io.koraframework.openapi.management;

import io.koraframework.http.common.body.HttpBody;
import io.koraframework.http.common.header.HttpHeaders;
import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.response.HttpServerResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.function.Supplier;
import java.util.zip.GZIPOutputStream;

final class CacheHttpServerResponse {

    private static final String ACCEPT_ENCODING = "Accept-Encoding";
    private static final String CONTENT_ENCODING = "Content-Encoding";
    private static final String VARY = "Vary";
    private static final String GZIP = "gzip";

    private final String contentType;
    private final OpenApiManagementConfig.CacheMode cacheMode;
    private final Supplier<byte[]> contentLoader;

    private volatile byte[] gzip;
    private volatile byte[] plain;

    CacheHttpServerResponse(String contentType, OpenApiManagementConfig.CacheMode cacheMode, Supplier<byte[]> contentLoader) {
        this.contentType = contentType;
        this.cacheMode = cacheMode;
        this.contentLoader = contentLoader;
    }

    HttpServerResponse response(HttpServerRequest request) {
        if (acceptsGzip(request)) {
            var headers = HttpHeaders.of(CONTENT_ENCODING, GZIP, VARY, ACCEPT_ENCODING);
            return switch (cacheMode) {
                case NONE -> HttpServerResponse.of(200, headers, HttpBody.of(contentType, gzip(contentLoader.get())));
                case GZIP, FULL -> HttpServerResponse.of(200, headers, HttpBody.of(contentType, gzip()));
            };
        } else {
            var headers = HttpHeaders.of(VARY, ACCEPT_ENCODING);
            return switch (cacheMode) {
                case NONE, GZIP -> HttpServerResponse.of(200, headers, HttpBody.of(contentType, contentLoader.get()));
                case FULL -> HttpServerResponse.of(200, headers, HttpBody.of(contentType, plain()));
            };
        }
    }

    private byte[] plain() {
        var result = this.plain;
        if (result == null) {
            synchronized (this) {
                result = this.plain;
                if (result == null) {
                    result = contentLoader.get();
                    this.plain = result;
                }
            }
        }
        return result;
    }

    private byte[] gzip() {
        var result = this.gzip;
        if (result == null) {
            synchronized (this) {
                result = this.gzip;
                if (result == null) {
                    result = gzip(contentLoader.get());
                    this.gzip = result;
                }
            }
        }
        return result;
    }

    private static boolean acceptsGzip(HttpServerRequest request) {
        var headers = request.headers();
        var value = headers.getFirst(ACCEPT_ENCODING);
        if (value == null || value.isBlank()) {
            return false;
        }
        for (var part : value.split(",")) {
            var encoding = part.trim().toLowerCase(Locale.ROOT);
            var parameters = encoding.split(";");
            if (!parameters[0].trim().equals(GZIP)) {
                continue;
            }
            for (var parameter : parameters) {
                if (parameter.trim().matches("q=0(?:\\.0*)?")) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private static byte[] gzip(byte[] content) {
        try {
            var out = new ByteArrayOutputStream(content.length);
            try (var gzip = new GZIPOutputStream(out)) {
                gzip.write(content);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Can't gzip OpenAPI UI resource", e);
        }
    }
}
