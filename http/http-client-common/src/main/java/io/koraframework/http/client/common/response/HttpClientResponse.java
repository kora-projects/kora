package io.koraframework.http.client.common.response;

import io.koraframework.http.common.body.HttpBodyInput;
import io.koraframework.http.common.header.HttpHeaders;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;

public interface HttpClientResponse extends Closeable {

    int code();

    HttpHeaders headers();

    HttpBodyInput body();

    @Override
    void close() throws IOException;

    record Default(int code, HttpHeaders headers, HttpBodyInput body, Runnable closer) implements HttpClientResponse {

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
            return "HttpClientResponse{code=" + code() +
                   ", bodyLength=" + ((body != null) ? body.contentLength() : -1) +
                   ", bodyType=" + ((body != null) ? body.contentType() : -1) +
                   '}';
        }
    }
}
