package ru.tinkoff.kora.common.util.flow;

import ru.tinkoff.kora.common.Context;

import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ErrorSubscription<T> extends AtomicBoolean implements Flow.Subscription {
    private final Flow.Subscriber<? super T> subscriber;
    private final Context context;
    private final Throwable error;

    public ErrorSubscription(Flow.Subscriber<? super T> subscriber, Context context, Throwable error) {
        this.subscriber = subscriber;
        this.context = context;
        this.error = error;
    }

    @Override
    public void request(long n) {
        assert n > 0;
        if (this.compareAndSet(false, true)) {
            var ctx = Context.current();
            this.context.inject();
            try {
                this.subscriber.onError(this.error);
            } finally {
                ctx.inject();
            }
        }
    }

    @Override
    public void cancel() {
        this.set(true);
    }
}
