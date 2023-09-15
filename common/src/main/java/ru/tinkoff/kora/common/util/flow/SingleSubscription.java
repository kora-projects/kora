package ru.tinkoff.kora.common.util.flow;

import ru.tinkoff.kora.common.Context;

import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SingleSubscription<T> extends AtomicBoolean implements Flow.Subscription {
    private final Flow.Subscriber<? super T> subscriber;
    private final Context context;
    private final T value;

    public SingleSubscription(Flow.Subscriber<? super T> subscriber, Context context, T value) {
        this.subscriber = subscriber;
        this.context = context;
        this.value = value;
    }

    @Override
    public void request(long n) {
        assert n > 0;
        if (this.compareAndSet(false, true)) {
            var subscriber = this.subscriber;
            var ctx = Context.current();
            this.context.inject();
            try {
                subscriber.onNext(this.value);
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
