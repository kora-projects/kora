package io.koraframework.http.server.netty.body;

import io.koraframework.http.server.netty.buffer.*;
import io.netty.buffer.*;

import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class KoraAvailableBody implements AutoCloseable {
    private static final String ALREADY_CONSUMED_MSG = "Body has already been consumed";

    private static final AtomicIntegerFieldUpdater<KoraAvailableBody> CONSUMED_UPDATER = AtomicIntegerFieldUpdater.newUpdater(KoraAvailableBody.class, "consumed");

    private final KoraReadBuffer buffer;
    private volatile int consumed;

    public KoraAvailableBody(KoraReadBuffer buffer) {
        this.buffer = Objects.requireNonNull(buffer, "buffer");
    }

    public long length() {
        return buffer.readable();
    }

    public KoraAvailableBody split() {
        if (consumed != 0) {
            throw new IllegalStateException(ALREADY_CONSUMED_MSG);
        }
        return new KoraAvailableBody(buffer.duplicate());
    }

    public byte[] toArray() {
        markConsumed();
        return buffer.toArray();
    }

    public String toString(Charset charset) {
        markConsumed();
        return buffer.toString(charset);
    }

    public InputStream toInputStream() {
        markConsumed();
        return buffer.toInputStream();
    }

    public Flow.Publisher<KoraReadBuffer> toPublisher() {
        markConsumed();
        return new AvailableBodyPublisher(buffer);
    }

    public ByteBuf toByteBuf() {
        markConsumed();
        return buffer.toByteBuf();
    }

    private void markConsumed() {
        if (!CONSUMED_UPDATER.compareAndSet(this, 0, 1)) {
            throw new IllegalStateException(ALREADY_CONSUMED_MSG);
        }
    }

    @Override
    public void close() {
        if (CONSUMED_UPDATER.compareAndSet(this, 0, 1)) {
            buffer.close();
        }
    }

    private static final class AvailableBodyPublisher implements Flow.Publisher<KoraReadBuffer> {
        private final KoraReadBuffer buffer;
        private final AtomicBoolean subscribed = new AtomicBoolean(false);

        AvailableBodyPublisher(KoraReadBuffer buffer) {
            this.buffer = buffer;
        }

        @Override
        public void subscribe(Flow.Subscriber<? super KoraReadBuffer> subscriber) {
            Objects.requireNonNull(subscriber, "subscriber");
            if (!subscribed.compareAndSet(false, true)) {
                subscriber.onSubscribe(new Flow.Subscription() {
                    @Override
                    public void request(long n) {
                    }

                    @Override
                    public void cancel() {
                    }
                });
                subscriber.onError(new IllegalStateException("Body publisher can only be subscribed to once"));
                return;
            }
            subscriber.onSubscribe(new AvailableBodySubscription(subscriber, buffer));
        }
    }

    private static final class AvailableBodySubscription implements Flow.Subscription {
        private final Flow.Subscriber<? super KoraReadBuffer> subscriber;
        private final KoraReadBuffer buffer;
        private final AtomicBoolean done = new AtomicBoolean(false);

        AvailableBodySubscription(Flow.Subscriber<? super KoraReadBuffer> subscriber, KoraReadBuffer buffer) {
            this.subscriber = subscriber;
            this.buffer = buffer;
        }

        @Override
        public void request(long n) {
            if (!done.compareAndSet(false, true)) {
                return;
            }
            if (n <= 0) {
                buffer.close();
                subscriber.onError(new IllegalArgumentException("Spec rule 3.9: n must be > 0"));
                return;
            }
            subscriber.onNext(buffer);
            subscriber.onComplete();
        }

        @Override
        public void cancel() {
            if (done.compareAndSet(false, true)) {
                buffer.close();
            }
        }
    }
}
