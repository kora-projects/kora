package io.koraframework.http.client.common.response;

import io.koraframework.http.common.body.HttpBody;
import io.koraframework.http.common.body.HttpBodyInput;
import io.koraframework.http.common.header.HttpHeaders;

import java.io.IOException;
import java.io.UncheckedIOException;

public record SimpleHttpClientResponse(int code, HttpHeaders headers, HttpBodyInput body, Runnable closer) implements HttpClientResponse {

    private static final Runnable NOOP_CLOSER = () -> {};

    public SimpleHttpClientResponse(int code, HttpHeaders headers, HttpBodyInput body) {
        this(code, headers, body, NOOP_CLOSER);
    }

    public SimpleHttpClientResponse(int code, HttpHeaders headers) {
        this(code, headers, HttpBody.empty(), NOOP_CLOSER);
    }

    public SimpleHttpClientResponse(int code) {
        this(code, HttpHeaders.empty(), HttpBody.empty(), NOOP_CLOSER);
    }

    @Override
    public void close() throws IOException {
        try {
            closer.run();
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    @Override
    public String toString() {
        return "SimpleHttpClientResponse{code=" + code() +
               ", bodyLength=" + ((body != null) ? body.contentLength() : -1) +
               ", bodyType=" + ((body != null) ? body.contentType() : -1) +
               ", headers=" + headers.size() +
               '}';
    }
}
