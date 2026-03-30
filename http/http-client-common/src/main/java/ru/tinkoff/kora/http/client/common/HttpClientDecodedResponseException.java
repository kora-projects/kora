package ru.tinkoff.kora.http.client.common;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

public class HttpClientDecodedResponseException extends HttpClientResponseException {

    private final Object body;

    public HttpClientDecodedResponseException(int code, HttpHeaders headers, @Nullable Object body) {
        super("HTTP response with status code %d".formatted(code), code, headers, new byte[0]);
        this.body = body;
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
