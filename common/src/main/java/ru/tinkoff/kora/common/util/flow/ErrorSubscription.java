package ru.tinkoff.kora.common.util.flow;

import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ErrorSubscription<T> extends AtomicBoolean implements Flow.Subscription {
    private final Flow.Subscriber<? super T> subscriber;
    private final Throwable error;

    public ErrorSubscription(Flow.Subscriber<? super T> subscriber, Throwable error) {
        this.subscriber = subscriber;
        this.error = error;
    }

    @Override
    public void request(long n) {
        assert n > 0;
        if (this.compareAndSet(false, true)) {
            this.subscriber.onError(this.error);
        }
    }

    @Override
    public void cancel() {
        this.set(true);
    }
}
