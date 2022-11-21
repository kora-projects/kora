package ru.tinkoff.kora.http.client.common;

public class HttpClientConnectionException extends HttpClientException {
    public HttpClientConnectionException(Exception cause) {
        super(cause);
    }
}
