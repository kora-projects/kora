package ru.tinkoff.kora.http.common.body;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.util.FlowUtils;

import java.nio.ByteBuffer;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

public class StreamingHttpBodyInput extends AtomicBoolean implements HttpBodyInput {
    @Nullable
    private final String contentType;
    private final int contentLength;
    private final Flow.Publisher<ByteBuffer> content;

    public StreamingHttpBodyInput(@Nullable String contentType, int contentLength, Flow.Publisher<ByteBuffer> content) {
        this.contentType = contentType;
        this.contentLength = contentLength;
        this.content = content;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        if (this.compareAndSet(false, true)) {
            content.subscribe(subscriber);
        } else {
            throw new IllegalStateException("Body was already subscribed");
        }
    }

    @Override
    public void close() {
        if (this.compareAndSet(false, true)) {
            content.subscribe(FlowUtils.drain());
        }
    }

    @Override
    public long contentLength() {
        return this.contentLength;
    }

    @Nullable
    @Override
    public String contentType() {
        return this.contentType;
    }
}
