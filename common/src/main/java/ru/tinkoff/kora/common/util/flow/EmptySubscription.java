package ru.tinkoff.kora.common.util.flow;

import ru.tinkoff.kora.common.Context;

import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

public final class EmptySubscription<T> extends AtomicBoolean implements Flow.Subscription {
    private final Context context;
    private final Flow.Subscriber<? super T> subscriber;

    public EmptySubscription(Context context, Flow.Subscriber<? super T> subscriber) {
        this.context = context;
        this.subscriber = subscriber;
    }

    @Override
    public void request(long n) {
        assert n > 0;
        if (this.compareAndSet(false, true)) {
            var subscriber = this.subscriber;
            var ctx = Context.current();
            this.context.inject();
            try {
                subscriber.onComplete();
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
