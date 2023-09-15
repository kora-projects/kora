package ru.tinkoff.kora.common.util.flow;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

public final class FutureSubscriber<T> extends CompletableFuture<T> implements Flow.Subscriber<T> {
    private volatile Flow.Subscription subscription;

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        subscription.request(2);
        this.subscription = subscription;
    }

    @Override
    public void onNext(T item) {
        this.complete(item);
        this.subscription.cancel();
    }

    @Override
    public void onError(Throwable throwable) {
        this.completeExceptionally(throwable);
        this.subscription.cancel();
    }

    @Override
    public void onComplete() {
        this.complete(null);
    }
}
