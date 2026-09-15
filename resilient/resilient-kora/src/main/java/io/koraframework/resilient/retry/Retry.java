package io.koraframework.resilient.retry;

import io.koraframework.resilient.common.ThrowableCallable;
import io.koraframework.resilient.common.ThrowableRunnable;
import io.koraframework.resilient.retry.exception.RetryExhaustedException;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public interface Retry {

    default boolean isFailure(Throwable throwable) {
        return !(throwable instanceof NonRetryableException);
    }

    interface RetryState extends AutoCloseable {

        enum RetryStatus {
            ACCEPTED,
            REJECTED,
            EXHAUSTED
        }

        RetryStatus onException(Throwable throwable);

        int getAttempts();

        int getAttemptsMax();

        long getDelayNanos();

        void doDelay();

        @Override
        void close();
    }

    RetryState asState();

    <E extends Throwable> void retry(ThrowableRunnable<E> runnable) throws RetryExhaustedException, E;

    <T, E extends Throwable> T retry(ThrowableCallable<T, E> callable) throws RetryExhaustedException, E;

    <T, E extends Throwable> T retry(ThrowableCallable<T, E> callable, ThrowableCallable<T, E> fallback) throws E;

    <T> CompletionStage<T> retry(Supplier<CompletionStage<T>> supplier);
}
