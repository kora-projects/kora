package ru.tinkoff.kora.http.client.common.response;

import ru.tinkoff.kora.http.common.body.HttpBodyInput;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

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
    }
}
