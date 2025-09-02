package ru.tinkoff.kora.http.server.common.handler;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.common.HttpResponseEntity;
import ru.tinkoff.kora.http.common.body.HttpBodyOutput;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.HttpServerResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Objects;

public class HttpServerResponseEntityMapper<T> implements HttpServerResponseMapper<HttpResponseEntity<T>> {
    private final HttpServerResponseMapper<T> delegate;

    public HttpServerResponseEntityMapper(HttpServerResponseMapper<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public HttpServerResponse apply(Context ctx, HttpServerRequest request, HttpResponseEntity<T> result) throws IOException {
        Objects.requireNonNull(result);

        var response = this.delegate.apply(ctx, request, result.body());

        final HttpBodyOutput body;
        final String contentType = result.headers().getFirst("content-type");
        if (contentType != null && !contentType.isEmpty()) {
            body = new HttpBodyWithDelegate(contentType, response.body().contentLength(), response.body());
        } else {
            body = response.body();
        }

        if (result.headers().isEmpty()) {
            return HttpServerResponse.of(result.code(), response.headers(), body);
        } else if (response.headers().isEmpty()) {
            return HttpServerResponse.of(result.code(), result.headers(), body);
        }

        var headers = HttpHeaders.of();
        for (var header : response.headers()) {
            headers.set(header.getKey(), header.getValue());
        }
        for (var header : result.headers()) {
            headers.add(header.getKey(), header.getValue());
        }

        return HttpServerResponse.of(result.code(), headers, body);
    }

    private static class HttpBodyWithDelegate implements HttpBodyOutput {
        private final String contentType;
        private final long len;
        private final HttpBodyOutput delegate;

        private HttpBodyWithDelegate(String contentType, long len, HttpBodyOutput delegate) {
            this.contentType = contentType;
            this.len = len;
            this.delegate = delegate;
        }

        @Override
        public long contentLength() {
            return len;
        }

        @Nullable
        @Override
        public String contentType() {
            return contentType;
        }

        @Nullable
        @Override
        public ByteBuffer getFullContentIfAvailable() {
            return HttpBodyOutput.super.getFullContentIfAvailable();
        }

        @Override
        public void write(OutputStream os) throws IOException {
            delegate.write(os);
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }
    }
}
