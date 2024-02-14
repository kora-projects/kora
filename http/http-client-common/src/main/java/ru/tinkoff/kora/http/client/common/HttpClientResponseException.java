package ru.tinkoff.kora.http.client.common;

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

    public HttpClientResponseException(int code, HttpHeaders headers, byte[] bytes) {
        super("HTTP response with status code %d:\n%s".formatted(code, new String(bytes, StandardCharsets.UTF_8)));
        this.code = code;
        this.headers = headers;
        this.bytes = bytes;
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
}
