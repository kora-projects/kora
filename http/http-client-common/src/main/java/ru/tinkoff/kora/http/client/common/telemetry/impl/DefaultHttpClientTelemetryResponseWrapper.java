package ru.tinkoff.kora.http.client.common.telemetry.impl;

import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.common.body.HttpBodyInput;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

import java.io.IOException;

public class DefaultHttpClientTelemetryResponseWrapper implements HttpClientResponse {
    private final HttpClientResponse delegate;
    private final HttpBodyInput wrappedBody;

    public DefaultHttpClientTelemetryResponseWrapper(HttpClientResponse delegate, HttpBodyInput wrappedBody) {
        this.delegate = delegate;
        this.wrappedBody = wrappedBody;
    }

    @Override
    public int code() {
        return delegate.code();
    }

    @Override
    public HttpHeaders headers() {
        return delegate.headers();
    }

    @Override
    public HttpBodyInput body() {
        return wrappedBody;
    }

    @Override
    public void close() throws IOException {
        try {
            wrappedBody.close();
        } finally {
            delegate.close();
        }
    }

    @Override
    public String toString() {
        return "HttpClientResponse{code=" + code() +
               ", bodyLength=" + delegate.body().contentLength() +
               ", bodyType=" + delegate.body().contentType() +
               '}';
    }
}
