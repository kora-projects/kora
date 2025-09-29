package ru.tinkoff.kora.http.common.body;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.util.flow.DrainSubscriber;

import java.nio.ByteBuffer;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.atomic.AtomicBoolean;

public class BlockingHttpBodyOutput extends AtomicBoolean implements HttpBodyOutput {
    @Nullable
    private final String contentType;
    private final long contentLength;
    private final Publisher<? extends ByteBuffer> content;

    public BlockingHttpBodyOutput(@Nullable String contentType, long contentLength, Publisher<? extends ByteBuffer> content) {
        this.contentType = contentType;
        this.contentLength = contentLength;
        this.content = content;
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

    @Override
    public void subscribe(Subscriber<? super ByteBuffer> subscriber) {
        if (this.compareAndSet(false, true)) {
            this.content.subscribe(subscriber);
        } else {
            throw new IllegalStateException("Body was already subscribed");
        }
    }

    @Override
    public void close() {
        if (this.compareAndSet(false, true)) {
            this.content.subscribe(new DrainSubscriber<>());
        }
    }
}
