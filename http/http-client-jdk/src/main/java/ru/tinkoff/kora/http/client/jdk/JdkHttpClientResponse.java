package ru.tinkoff.kora.http.client.jdk;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.common.body.HttpBodyInput;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.Flow;

public class JdkHttpClientResponse implements HttpClientResponse {
    private final HttpResponse<InputStream> response;
    private final JdkHttpClientHeaders headers;
    private final BodyPublisher body;

    public JdkHttpClientResponse(HttpResponse<InputStream> response) {
        this.response = response;
        this.headers = new JdkHttpClientHeaders(this.response.headers());
        this.body = new BodyPublisher(response);
    }

    @Override
    public int code() {
        return this.response.statusCode();
    }

    @Override
    public HttpHeaders headers() {
        return this.headers;
    }

    @Override
    public HttpBodyInput body() {
        return this.body;
    }

    @Override
    public void close() throws IOException {
        this.body.close();
    }

    @Override
    public String toString() {
        return "HttpClientResponse{code=" + code() +
            ", bodyLength=" + body.contentLength() +
            ", bodyType=" + body.contentType() +
            '}';
    }

    private static final class BodyPublisher implements HttpBodyInput {

        private static final String EMPTY_CONTENT_TYPE = "<UNKNOWN-CONTENT-TYPE\r\n>";
        private static final long EMPTY_CONTENT_LENGTH = -2;

        private final java.net.http.HttpHeaders headers;

        private final InputStream is;
        private volatile long contentLength = EMPTY_CONTENT_LENGTH;
        private volatile String contentType = EMPTY_CONTENT_TYPE;

        public BodyPublisher(HttpResponse<InputStream> response) {
            this.headers = response.headers();
            this.is = response.body();
        }

        @Override
        public long contentLength() {
            var contentLength = this.contentLength;
            if (contentLength == EMPTY_CONTENT_LENGTH) {
                this.contentLength = contentLength = headers.firstValueAsLong("content-length").orElse(-1);
            }
            return contentLength;
        }

        @Nullable
        @Override
        public String contentType() {
            var contentType = this.contentType;
            if (Objects.equals(contentType, EMPTY_CONTENT_TYPE)) {
                this.contentType = contentType = headers.firstValue("content-type").orElse(null);
            }
            return contentType;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
            throw new IllegalStateException();
        }

        @Override
        public InputStream asInputStream() {
            return this.is;
        }


        @Override
        public void close() throws IOException {
            this.is.close();
        }
    }
}
