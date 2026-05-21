package io.koraframework.http.client.common.exception;

import io.koraframework.http.common.header.HttpHeaders;
import org.jspecify.annotations.Nullable;

public class HttpClientDecodedResponseException extends HttpClientResponseException {

    private final @Nullable Object body;

    public HttpClientDecodedResponseException(int code, HttpHeaders headers, @Nullable Object body) {
        super("HTTP response with status code %d".formatted(code), code, headers, new byte[0]);
        this.body = body;
    }

    @SuppressWarnings("unchecked")
    public <T> T body() {
        return (T) body;
    }

    public <T> @Nullable T body(Class<T> type) {
        if (type.isInstance(body)) {
            return type.cast(body);
        }
        return null;
    }
}
