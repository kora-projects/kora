package ru.tinkoff.kora.http.common.body;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.util.FlowUtils;

import java.nio.ByteBuffer;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

public class StreamingHttpInBody extends AtomicBoolean implements HttpInBody {
    @Nullable
    private final String contentType;
    private final int contentLength;
    private final Flow.Publisher<ByteBuffer> content;

    public StreamingHttpInBody(@Nullable String contentType, int contentLength, Flow.Publisher<ByteBuffer> content) {
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
    public int contentLength() {
        return this.contentLength;
    }

    @Nullable
    @Override
    public String contentType() {
        return this.contentType;
    }
}
