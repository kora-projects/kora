package ru.tinkoff.kora.common.util.flow;

import ru.tinkoff.kora.common.Context;

import java.util.concurrent.Flow;

public class OnePublisher<T> implements Flow.Publisher<T> {
    private final Context context;
    private final T value;

    public OnePublisher(Context context, T value) {
        this.context = context;
        this.value = value;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        var s = new SingleSubscription<>(subscriber, context, value);
        subscriber.onSubscribe(s);
    }

    public T value() {
        return value;
    }
}
