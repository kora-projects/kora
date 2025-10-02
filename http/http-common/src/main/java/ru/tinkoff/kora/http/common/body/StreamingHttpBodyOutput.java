package ru.tinkoff.kora.http.common.body;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.util.flow.DrainSubscriber;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.atomic.AtomicBoolean;

public class StreamingHttpBodyOutput extends AtomicBoolean implements HttpBodyOutput {

    @FunctionalInterface
    public interface IOConsumer<T> {

        void accept(T value) throws IOException;
    }

    @Nullable
    private final String contentType;
    private final long contentLength;

    // available either one of them
    @Nullable
    private final Publisher<? extends ByteBuffer> content;
    @Nullable
    private final IOConsumer<OutputStream> outputStreamConsumer;

    public StreamingHttpBodyOutput(@Nullable String contentType, long contentLength, Publisher<? extends ByteBuffer> content) {
        this.contentType = contentType;
        this.contentLength = contentLength;
        this.content = content;
        this.outputStreamConsumer = null;
    }

    public StreamingHttpBodyOutput(@Nullable String contentType, long contentLength, IOConsumer<OutputStream> outputStreamConsumer) {
        this.contentType = contentType;
        this.contentLength = contentLength;
        this.content = null;
        this.outputStreamConsumer = outputStreamConsumer;
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
            if (this.content == null) {
                throw new IllegalStateException("HttpBody is blocking and can't be subscribed");
            } else {
                this.content.subscribe(subscriber);
            }
        } else {
            throw new IllegalStateException("Body was already subscribed");
        }
    }

    @Override
    public void write(OutputStream os) throws IOException {
        if (outputStreamConsumer != null) {
            outputStreamConsumer.accept(os);
        } else {
            HttpBodyOutput.super.write(os);
        }
    }

    @Override
    public void close() {
        if (this.content != null) {
            if (this.compareAndSet(false, true)) {
                this.content.subscribe(new DrainSubscriber<>());
            }
        }
    }
}
