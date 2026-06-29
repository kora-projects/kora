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
                    QueuePublisher.this.drain();
                }
            }

            @Override
            public void cancel() {
                QueuePublisher.this.cancel();
            }
        });
    }

    private void drain() {
        if (WIP.getAndAdd(QueuePublisher.this, 1) != 0) {
            return;
        }
        var missed = 1;
        while (true) {
            if (get()) {
                return;
            }
            var completed = this.drainLoop();
            if (completed) {
                return;
            }
            missed = WIP.addAndGet(this, -missed);
            if (missed == 0) {
                break;
            }
        }
    }

    private boolean drainLoop() {
        var queue = this.queue;
        while (this.demand > 0) {
            if (get()) {
                return true;
            }
            var item = queue.poll();
            if (item == null) {
                return false;
            }
            if (item.value != null) {
                DEMAND.decrementAndGet(this);
                delegate.onNext(item.value);
            } else if (item.error != null) {
                delegate.onError(item.error);
            } else {
                delegate.onComplete();
                return true;
            }
        }
        return false;
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
        this.drain();
    }

    public void error(Throwable throwable) {
        try {
            queue.put(new Signal<>(null, throwable));
        } catch (InterruptedException e) {
            // nvm
        }
        this.drain();
    }

    public void complete() {
        try {
            queue.put(new Signal<>(null, null));
        } catch (InterruptedException e) {
            // nvm
        }
        this.drain();
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
