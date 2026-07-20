package io.koraframework.http.client.common.exception;

import io.koraframework.http.client.common.response.HttpClientResponse;
import io.koraframework.http.common.header.HttpHeaders;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class HttpClientResponseException extends HttpClientException {

    private final int code;
    private final HttpHeaders headers;
    private final byte[] body;

    public HttpClientResponseException(int code, HttpHeaders headers, byte[] body) {
        super("HTTP response with status code %d:\n%s".formatted(code, new String(body, StandardCharsets.UTF_8)));
        this.code = code;
        this.headers = headers;
        this.body = body;
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

    public byte[] getBody() {
        return body;
    }
}
