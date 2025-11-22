package ru.tinkoff.kora.common.util.flow;

import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SingleSubscription<T> extends AtomicBoolean implements Flow.Subscription {
    private final Flow.Subscriber<? super T> subscriber;
    private final T value;

    public SingleSubscription(Flow.Subscriber<? super T> subscriber, T value) {
        this.subscriber = subscriber;
        this.value = value;
    }

    @Override
    public void request(long n) {
        assert n > 0;
        if (this.compareAndSet(false, true)) {
            var subscriber = this.subscriber;
            subscriber.onNext(this.value);
            subscriber.onComplete();
        }
    }

    @Override
    public void cancel() {
        this.set(true);
    }
}
