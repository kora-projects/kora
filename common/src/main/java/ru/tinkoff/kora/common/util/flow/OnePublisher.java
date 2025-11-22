package ru.tinkoff.kora.common.util.flow;

import java.util.concurrent.Flow;

public class OnePublisher<T> implements Flow.Publisher<T> {
    private final T value;

    public OnePublisher(T value) {
        this.value = value;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        var s = new SingleSubscription<>(subscriber, value);
        subscriber.onSubscribe(s);
    }

    public T value() {
        return value;
    }
}
