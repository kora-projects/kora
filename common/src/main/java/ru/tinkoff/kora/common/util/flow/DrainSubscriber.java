package ru.tinkoff.kora.common.util.flow;

import java.util.concurrent.Flow;

public class DrainSubscriber<T> implements Flow.Subscriber<T> {
    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(T item) {

    }

    @Override
    public void onError(Throwable throwable) {

    }

    @Override
    public void onComplete() {

    }
}
