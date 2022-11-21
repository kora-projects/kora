package ru.tinkoff.kora.common.util;

import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Signal;
import reactor.util.context.Context;

import javax.annotation.Nonnull;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class ByteBufferFluxInputStream extends InputStream implements CoreSubscriber<ByteBuffer> {
    private final BlockingQueue<Signal<ByteBuffer>> queue = new ArrayBlockingQueue<>(4);
    private final AtomicInteger demand = new AtomicInteger(1);
    private final Context context = ru.tinkoff.kora.common.Context.Reactor.inject(Context.empty(), ru.tinkoff.kora.common.Context.current());
    private ByteBuffer currentBuffer = null;
    private volatile Subscription subscription = null;
    private volatile boolean completed = false;

    public ByteBufferFluxInputStream(Flux<ByteBuffer> byteBufferFlux) {
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
                    this.currentBuffer = signal.get();
                } else if (signal.isOnError()) {
                    this.completed = true;
                    throw toRuntimeException(signal.getThrowable());
                } else if (signal.isOnComplete()) {
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
    public void onSubscribe(Subscription s) {
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

    @Override
    @Nonnull
    public Context currentContext() {
        return this.context;
    }
}
