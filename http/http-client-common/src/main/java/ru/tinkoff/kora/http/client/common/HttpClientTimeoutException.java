package ru.tinkoff.kora.http.client.common;

import java.io.InterruptedIOException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

public class HttpClientTimeoutException extends HttpClientException {
    public HttpClientTimeoutException(TimeoutException cause) {
        super(cause);
    }
    public HttpClientTimeoutException(InterruptedIOException cause) {
        super(cause);
    }

    public HttpClientTimeoutException(HttpTimeoutException cause) {
        super(cause);
    }
}
