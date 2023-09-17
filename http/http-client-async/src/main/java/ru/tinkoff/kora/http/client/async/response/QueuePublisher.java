package ru.tinkoff.kora.http.client.async.response;

import jakarta.annotation.Nullable;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

public class QueuePublisher<T> extends AtomicBoolean implements Flow.Publisher<T> {
    private final BlockingQueue<Signal<T>> queue = new ArrayBlockingQueue<>(16);
    private volatile Flow.Subscriber<? super T> delegate;

    @SuppressWarnings("unchecked")
    private static final AtomicLongFieldUpdater<QueuePublisher<?>> DEMAND = (((AtomicLongFieldUpdater<QueuePublisher<?>>) ((AtomicLongFieldUpdater) AtomicLongFieldUpdater.newUpdater(
        QueuePublisher.class, "demand"
    ))));
    private volatile long demand = 0;

    @SuppressWarnings("unchecked")
    private static final AtomicIntegerFieldUpdater<QueuePublisher<?>> WIP = (((AtomicIntegerFieldUpdater<QueuePublisher<?>>) ((AtomicIntegerFieldUpdater) AtomicIntegerFieldUpdater.newUpdater(
        QueuePublisher.class, "wip"
    ))));
    private volatile int wip = 0;

    public QueuePublisher() {
        super(false);
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        this.delegate = subscriber;
        subscriber.onSubscribe(new Flow.Subscription() {
            @Override
            public void request(long n) {
                if (DEMAND.getAndAdd(QueuePublisher.this, n) <= 0) {
                    if (WIP.compareAndSet(QueuePublisher.this, 0, 1)) {
                        QueuePublisher.this.drainLoop();
                    }
                }
            }

            @Override
            public void cancel() {
                QueuePublisher.this.cancel();
            }
        });
    }

    private void drainLoop() {
        try {
            var queue = this.queue;
            while (this.demand > 0) {
                if (get()) {
                    return;
                }
                var item = queue.poll();
                if (item == null) {
                    return;
                }
                if (item.value != null) {
                    DEMAND.decrementAndGet(this);
                    delegate.onNext(item.value);
                } else if (item.error != null) {
                    delegate.onError(item.error);
                } else {
                    delegate.onComplete();
                }
            }
        } finally {
            WIP.set(this, 0);
        }
    }

    public void next(T item) {
        if (get()) {
            return;
        }
        try {
            this.queue.put(new Signal<>(item, null));
        } catch (InterruptedException e) {
            this.set(true);
            delegate.onError(e);
            return;
        }
        if (WIP.compareAndSet(QueuePublisher.this, 0, 1)) {
            this.drainLoop();
        }
    }

    public void error(Throwable throwable) {
        try {
            queue.put(new Signal<>(null, throwable));
        } catch (InterruptedException e) {
            // nvm
        }
    }

    public void complete() {
        try {
            queue.put(new Signal<>(null, null));
        } catch (InterruptedException e) {
            // nvm
        }
        if (WIP.compareAndSet(QueuePublisher.this, 0, 1)) {
            this.drainLoop();
        }
    }

    public void cancel() {
        try {
            queue.put(new Signal<>(null, new CancellationException()));
        } catch (InterruptedException e) {
            // nvm
        }
        this.set(true);
    }

    private static final class Signal<T> {
        @Nullable
        private final T value;
        @Nullable
        private final Throwable error;

        private Signal(T value, Throwable error) {
            assert value == null || error == null;
            this.value = value;
            this.error = error;
        }
    }
}
