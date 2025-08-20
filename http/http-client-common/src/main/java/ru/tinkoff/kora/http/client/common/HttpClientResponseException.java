package ru.tinkoff.kora.http.client.common;

import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

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


    public static HttpClientResponseException fromResponse(HttpClientResponse response) throws IOException {
        try (var body = response.body()) {
            var full = body.getFullContentIfAvailable();
            if (full != null) {
                var bytes = new byte[full.remaining()];
                full.get(bytes);
                return new HttpClientResponseException(response.code(), response.headers(), bytes);
            }
            try (var is = body.asInputStream()) {
                var bytes = is.readNBytes(4096);
                return new HttpClientResponseException(response.code(), response.headers(), bytes);
            }
        }
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
