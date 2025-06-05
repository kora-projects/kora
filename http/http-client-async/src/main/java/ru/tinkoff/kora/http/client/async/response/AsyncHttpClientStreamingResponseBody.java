package ru.tinkoff.kora.http.client.async.response;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.util.FlowUtils;
import ru.tinkoff.kora.http.common.body.HttpBodyInput;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AsyncHttpClientStreamingResponseBody extends AtomicBoolean implements HttpBodyInput {

    private static final String EMPTY_CONTENT_TYPE = "<UNKNOWN-CONTENT-TYPE\r\n>";
    private static final long EMPTY_CONTENT_LENGTH = -2;

    private final HttpHeaders headers;
    private volatile long contentLength = EMPTY_CONTENT_LENGTH;
    private volatile String contentType = EMPTY_CONTENT_TYPE;

    private final Flow.Publisher<ByteBuffer> bodyStream;

    public AsyncHttpClientStreamingResponseBody(HttpHeaders headers, Flow.Publisher<ByteBuffer> bodyStream) {
        this.headers = headers;
        this.bodyStream = bodyStream;
    }

    @Override
    public long contentLength() {
        var contentLength = this.contentLength;
        if (contentLength == EMPTY_CONTENT_LENGTH) {
            this.contentLength = contentLength = Long.parseLong(headers.get(HttpHeaderNames.CONTENT_LENGTH));
        }
        return contentLength;
    }

    @Nullable
    @Override
    public String contentType() {
        var contentType = this.contentType;
        if (Objects.equals(contentType, EMPTY_CONTENT_TYPE)) {
            this.contentType = contentType = headers.get(HttpHeaderNames.CONTENT_TYPE);
        }
        return contentType;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        if (this.compareAndSet(false, true)) {
            this.bodyStream.subscribe(subscriber);
        }
    }

    @Override
    public void close() throws IOException {
        if (this.compareAndSet(false, true)) {
            this.bodyStream.subscribe(FlowUtils.drain());
        }
    }
}
