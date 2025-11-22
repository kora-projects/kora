package ru.tinkoff.kora.common.util.flow;

import java.util.concurrent.Callable;
import java.util.concurrent.Flow;

public class FromCallablePublisher<T> implements Flow.Publisher<T> {
    private final Callable<T> value;

    public FromCallablePublisher(Callable<T> value) {
        this.value = value;
    }

    @Override
    public void subscribe(Flow.Subscriber<? super T> subscriber) {
        var s = new LazySingleSubscription<>(subscriber, value);
        subscriber.onSubscribe(s);
    }

    public final Callable<T> callable() {
        return this.value;
    }
}
