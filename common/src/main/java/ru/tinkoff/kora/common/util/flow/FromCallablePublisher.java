package ru.tinkoff.kora.common.util.flow;

import ru.tinkoff.kora.common.Context;

import java.util.concurrent.Callable;
import java.util.concurrent.Flow;

public class FromCallablePublisher<T> implements Flow.Publisher<T> {
    private final Context context;
    private final Callable<T> value;

    public FromCallablePublisher(Context context, Callable<T> value) {
        this.context = context;
        this.value = value;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        var s = new LazySingleSubscription<>(subscriber, context, value);
        subscriber.onSubscribe(s);
    }

    public final Callable<T> callable() {
        return this.value;
    }
}
