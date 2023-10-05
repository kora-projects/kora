package ru.tinkoff.kora.http.client.common.telemetry;

import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.common.body.HttpInBody;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

import java.io.IOException;

public class DefaultHttpClientTelemetryResponseWrapper implements HttpClientResponse {
    private final HttpClientResponse delegate;
    private final HttpInBody wrappedBody;

    public DefaultHttpClientTelemetryResponseWrapper(HttpClientResponse delegate, HttpInBody wrappedBody) {
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
    public HttpInBody body() {
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
}
