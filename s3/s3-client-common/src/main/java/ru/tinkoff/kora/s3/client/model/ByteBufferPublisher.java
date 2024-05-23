package ru.tinkoff.kora.s3.client.model;

import ru.tinkoff.kora.common.util.FlowUtils;

import java.io.Closeable;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

final class ByteBufferPublisher extends AtomicBoolean implements Flow.Publisher<ByteBuffer>, Closeable {

    private final Flow.Publisher<? extends ByteBuffer> publisher;

    private volatile InputStreamByteBufferSubscriber is;

    public ByteBufferPublisher(Flow.Publisher<? extends ByteBuffer> response) {
        this.publisher = response;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super ByteBuffer> subscriber) {
        if (this.compareAndSet(false, true)) {
            this.publisher.subscribe(subscriber);
        } else {
            throw new IllegalStateException("Publishers was already subscribed");
        }
    }

    public InputStream asInputStream() {
        var is = this.is;
        if (is == null) {
            if (this.compareAndSet(false, true)) {
                this.is = is = new InputStreamByteBufferSubscriber();
                this.publisher.subscribe(is);
            } else {
                throw new IllegalStateException("Publishers was already subscribed");
            }
        }
        return is;
    }

    @Override
    public void close() {
        if (this.compareAndSet(false, true)) {
            this.publisher.subscribe(FlowUtils.drain());
        }
    }
}
