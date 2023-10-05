package ru.tinkoff.kora.http.client.async.response;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.util.FlowUtils;
import ru.tinkoff.kora.http.common.body.HttpBodyInput;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AsyncHttpClientStreamingResponseBody extends AtomicBoolean implements HttpBodyInput {
    private static final String UNKNOWN_CONTENT_TYPE = "<UNKNOWN-CONTENT-TYPE\r\n>";
    private final HttpHeaders headers;
    private String contentType;
    private int contentLength = -2;

    private final Flow.Publisher<ByteBuffer> bodyStream;

    public AsyncHttpClientStreamingResponseBody(HttpHeaders headers, Flow.Publisher<ByteBuffer> bodyStream) {
        this.headers = headers;
        this.bodyStream = bodyStream;
    }

    @Override
    public int contentLength() {
        var cl = this.contentLength;
        if (cl >= -1) {
            return cl;
        }
        var value = headers.get(HttpHeaderNames.CONTENT_LENGTH);
        if (value != null) {
            return this.contentLength = Integer.parseInt(value);
        } else {
            return this.contentLength = -1;
        }
    }

    @Nullable
    @Override
    public String contentType() {
        var ct = this.contentType;
        if (ct == UNKNOWN_CONTENT_TYPE) {
            return null;
        }
        if (ct != null) {
            return ct;
        }
        var value = headers.get(HttpHeaderNames.CONTENT_TYPE);
        if (value != null) {
            return this.contentType = value;
        } else {
            this.contentType = UNKNOWN_CONTENT_TYPE;
            return null;
        }
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
