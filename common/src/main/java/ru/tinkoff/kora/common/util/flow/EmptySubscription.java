package ru.tinkoff.kora.common.util.flow;

import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

public final class EmptySubscription<T> extends AtomicBoolean implements Flow.Subscription {
    private final Flow.Subscriber<? super T> subscriber;

    public EmptySubscription(Flow.Subscriber<? super T> subscriber) {
        this.subscriber = subscriber;
    }

    @Override
    public void request(long n) {
        assert n > 0;
        if (this.compareAndSet(false, true)) {
            var subscriber = this.subscriber;
            subscriber.onComplete();
        }
    }

    @Override
    public void cancel() {
        this.set(true);
    }
}
