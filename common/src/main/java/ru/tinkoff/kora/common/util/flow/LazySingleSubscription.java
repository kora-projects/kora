package ru.tinkoff.kora.common.util.flow;

import java.util.concurrent.Callable;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LazySingleSubscription<T> extends AtomicBoolean implements Flow.Subscription {
    private final Flow.Subscriber<? super T> subscriber;
    private final Callable<? extends T> value;

    public LazySingleSubscription(Flow.Subscriber<? super T> subscriber, Callable<? extends T> value) {
        this.subscriber = subscriber;
        this.value = value;
    }

    @Override
    public void request(long n) {
        assert n > 0;
        if (this.compareAndSet(false, true)) {
            var subscriber = this.subscriber;
            final T value;
            try {
                value = this.value.call();
            } catch (Throwable e) {
                subscriber.onError(e);
                return;
            }
            subscriber.onNext(value);
            subscriber.onComplete();
        }
    }

    @Override
    public void cancel() {
        this.set(true);
    }
}
