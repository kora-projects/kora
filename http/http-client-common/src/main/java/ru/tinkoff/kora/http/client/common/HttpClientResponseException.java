package ru.tinkoff.kora.http.client.common;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.util.FlowUtils;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class HttpClientResponseException extends HttpClientException {
    private final int code;
    private final HttpHeaders headers;
    private final byte[] bytes;
    @Nullable
    private final Object body;

    public HttpClientResponseException(int code, HttpHeaders headers, byte[] bytes) {
        this(code, headers, bytes, null);
    }

    public HttpClientResponseException(int code, HttpHeaders headers, byte[] bytes, @Nullable Object body) {
        super("HTTP response with status code %d:\n%s".formatted(code, new String(bytes, StandardCharsets.UTF_8)));
        this.code = code;
        this.headers = headers;
        this.bytes = bytes;
        this.body = body;
    }

    public static HttpClientResponseException of(int code, HttpHeaders headers, @Nullable Object body) {
        return new HttpClientResponseException(code, headers, new byte[0], body);
    }

    public static HttpClientResponseException ofRaw(HttpClientResponse response) {
        var full = response.body().getFullContentIfAvailable();
        if (full != null) {
            var bytes = new byte[full.remaining()];
            full.get(bytes);
            return new HttpClientResponseException(response.code(), response.headers(), bytes);
        }
        try {
            var bytes = FlowUtils.toByteArrayFuture(response.body(), 4096).get();
            if (bytes == null) {
                bytes = new byte[0];
            }
            return new HttpClientResponseException(response.code(), response.headers(), bytes);
        } catch (Exception e) {
            var ex = new HttpClientResponseException(response.code(), response.headers(), new byte[0]);
            ex.addSuppressed(e);
            return ex;
        }
    }

    public static <T> CompletionStage<T> fromResponse(HttpClientResponse response) {
        var full = response.body().getFullContentIfAvailable();
        if (full != null) {
            var bytes = new byte[full.remaining()];
            full.get(bytes);
            return CompletableFuture.failedFuture(new HttpClientResponseException(response.code(), response.headers(), bytes));
        }
        return FlowUtils.toByteArrayFuture(response.body(), 4096)
            .handle((bytes, error) -> {
                if (bytes == null) {
                    bytes = new byte[0];
                }
                var e = new HttpClientResponseException(response.code(), response.headers(), bytes);
                if (error != null) {
                    e.addSuppressed(error);
                }
                throw e;
            });
    }

    public static CompletableFuture<HttpClientResponseException> fromResponseFuture(HttpClientResponse response) {
        var full = response.body().getFullContentIfAvailable();
        if (full != null) {
            var bytes = new byte[full.remaining()];
            full.get(bytes);
            return CompletableFuture.completedFuture(new HttpClientResponseException(response.code(), response.headers(), bytes));
        }
        return FlowUtils.toByteArrayFuture(response.body(), 4096)
            .handle((bytes, error) -> {
                if (bytes == null) {
                    bytes = new byte[0];
                }
                var e = new HttpClientResponseException(response.code(), response.headers(), bytes);
                if (error != null) {
                    e.addSuppressed(error);
                }
                return e;
            });
    }

    public int getCode() {
        return code;
    }

    public HttpHeaders getHeaders() {
        return headers;
    }

    public byte[] getBytes() {
        return bytes;
    }

    @SuppressWarnings("unchecked")
    public <T> T body() {
        return (T) body;
    }

    public <T> T body(Class<T> type) {
        if (type.isInstance(body)) {
            return type.cast(body);
        }
        return null;
    }
}
