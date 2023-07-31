package ru.tinkoff.kora.http.client.common.response;

import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.common.util.ByteBufferFluxInputStream;
import ru.tinkoff.kora.http.client.common.HttpClientException;
import ru.tinkoff.kora.http.client.common.UnknownHttpClientException;
import ru.tinkoff.kora.http.common.HttpHeaders;

import javax.annotation.Nullable;
import java.io.InputStream;

public interface BlockingHttpResponse extends AutoCloseable {
    int code();

    HttpHeaders headers();

    InputStream body();

    @Override
    void close();

    static BlockingHttpResponse from(Mono<HttpClientResponse> responseMono) {
        HttpClientResponse response;
        try {
            response = responseMono.block();
        } catch (Exception e) {
            var unwrapped = Exceptions.unwrap(e);
            if (unwrapped instanceof HttpClientException httpClientException) {
                throw httpClientException;
            }
            throw new UnknownHttpClientException(unwrapped);
        }
        return new DefaultBlockingResponseImpl(response);
    }

    class DefaultBlockingResponseImpl implements BlockingHttpResponse {
        private final HttpClientResponse response;
        @Nullable
        private ByteBufferFluxInputStream body;

        public DefaultBlockingResponseImpl(HttpClientResponse response) {
            this.response = response;
        }

        @Override
        public int code() {
            return response.code();
        }

        @Override
        public HttpHeaders headers() {
            return response.headers();
        }

        @Override
        public ByteBufferFluxInputStream body() {
            var body = this.body;
            if (body != null) {
                return body;
            }
            body = new ByteBufferFluxInputStream(response.body());
            this.body = body;
            return body;
        }

        @Override
        public void close() {
            try {
                response.close().block();
            } catch (Exception e) {
                var unwrapped = Exceptions.unwrap(e);
                if (unwrapped instanceof HttpClientException httpClientException) {
                    throw httpClientException;
                }
                throw new UnknownHttpClientException(unwrapped);
            }
        }
    }
}
