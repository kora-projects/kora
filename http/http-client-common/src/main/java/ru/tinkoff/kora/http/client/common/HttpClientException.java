package ru.tinkoff.kora.http.client.common;

public abstract class HttpClientException extends RuntimeException {
    public HttpClientException(String message) {
        super(message);
    }

    public HttpClientException(String message, Throwable cause) {
        super(message, cause);
    }

    public HttpClientException(Throwable cause) {
        super(cause);
    }
}
