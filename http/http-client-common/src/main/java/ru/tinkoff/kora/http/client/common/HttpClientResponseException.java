package ru.tinkoff.kora.http.client.common;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.util.ReactorUtils;
import ru.tinkoff.kora.http.client.common.response.BlockingHttpResponse;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.common.HttpHeaders;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class HttpClientResponseException extends HttpClientException {
    private final int code;
    private final HttpHeaders headers;
    private final byte[] bytes;

    public HttpClientResponseException(int code, HttpHeaders headers, byte[] bytes) {
        super("Http response with error code %d:\n%s".formatted(code, new String(bytes, StandardCharsets.UTF_8)));
        this.code = code;
        this.headers = headers;
        this.bytes = bytes;
    }

    public static HttpClientException fromResponse(BlockingHttpResponse response) {
        try {
            var bytes = response.body().readAllBytes();
            return new HttpClientResponseException(response.code(), response.headers(), bytes);
        } catch (IOException e) {
            return new HttpClientConnectionException(e);
        }
    }

    public static <T> Mono<T> fromResponse(HttpClientResponse response) {
        return ReactorUtils.toByteArrayMono(response.body(), 4096)
            .handle((bytes, sink) -> sink.error(new HttpClientResponseException(response.code(), response.headers(), bytes)));
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
