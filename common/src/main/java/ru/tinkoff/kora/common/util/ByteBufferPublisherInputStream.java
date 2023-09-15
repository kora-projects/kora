package ru.tinkoff.kora.common.util;

import reactor.core.Exceptions;

import javax.annotation.Nonnull;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

public class ByteBufferPublisherInputStream extends InputStream implements Flow.Subscriber<ByteBuffer> {
    private final BlockingQueue<Signal> queue = new ArrayBlockingQueue<>(4);
    private final AtomicInteger demand = new AtomicInteger(1);
    private ByteBuffer currentBuffer = null;
    private volatile Flow.Subscription subscription = null;
    private volatile boolean completed = false;

    public ByteBufferPublisherInputStream(Flow.Publisher<ByteBuffer> byteBufferFlux) {
        byteBufferFlux.subscribe(this);
    }

    @Override
    public int read() {
        var b = new byte[1];
        var read = this.read(b, 0, 1);
        if (read <= 0) {
            return -1;
        }
        return b[0];
    }

    @Override
    public int read(@Nonnull byte[] b, int off, int len) {
        if (this.completed) {
            return -1;
        }
        while (this.currentBuffer == null || !this.currentBuffer.hasRemaining()) {
            if (this.demand.compareAndSet(0, 1)) {
                this.subscription.request(1);
            }
            try {
                var signal = this.queue.take();
                if (signal.isOnNext()) {
                    this.currentBuffer = signal.value;
                } else if (signal.isOnError()) {
                    this.completed = true;
                    throw toRuntimeException(signal.error);
                } else {
                    this.completed = true;
                    return -1;
                }
            } catch (InterruptedException e) {
                this.completed = true;
                if (this.subscription != null) {
                    this.subscription.cancel();
                }
                throw toRuntimeException(e);
            }
        }
        var realLen = Math.min(len, this.currentBuffer.remaining());
        this.currentBuffer.get(b, off, realLen);
        if (!this.currentBuffer.hasRemaining()) {
            this.currentBuffer = null;
        }
        return realLen;
    }

    @Override
    public void close() {
        if (this.completed) {
            return;
        }
        this.completed = true;
        this.subscription.cancel();
        this.currentBuffer = null;
        this.queue.clear();
    }

    private RuntimeException toRuntimeException(Throwable throwable) {
        var unwrap = Exceptions.unwrap(throwable);
        if (unwrap instanceof RuntimeException re) {
            return re;
        } else {
            return new RuntimeException(unwrap);
        }
    }

    @Override
    public void onSubscribe(Flow.Subscription s) {
        this.subscription = s;
        if (this.completed) {
            s.cancel();
        } else {
            s.request(1);
        }
    }

    @Override
    public void onNext(ByteBuffer byteBuffer) {
        if (this.completed) {
            return;
        }
        this.demand.decrementAndGet();
        this.queue.offer(Signal.next(byteBuffer));
    }

    @Override
    public void onError(Throwable t) {
        this.queue.offer(Signal.error(t));
    }

    @Override
    public void onComplete() {
        this.queue.offer(Signal.complete());
    }

    private static class Signal {
        private static final Signal COMPLETE = new Signal(null, null);
        private final ByteBuffer value;
        private final Throwable error;

        private Signal(ByteBuffer value, Throwable error) {
            this.value = value;
            this.error = error;
        }

        static Signal complete() {
            return COMPLETE;
        }

        static Signal error(Throwable t) {
            return new Signal(null, t);
        }

        static Signal next(ByteBuffer byteBuffer) {
            return new Signal(byteBuffer, null);
        }

        boolean isOnNext() {
            return value != null;
        }

        boolean isOnError() {
            return error != null;
        }
    }
}
