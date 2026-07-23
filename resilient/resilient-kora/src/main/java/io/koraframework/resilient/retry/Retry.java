package io.koraframework.resilient.retry;

import io.koraframework.resilient.retry.exception.RetryExhaustedException;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public interface Retry {

    default boolean test(Throwable throwable) {
        return true;
    }

    @FunctionalInterface
    interface RetryRunnable<E extends Throwable> {
        void run() throws E;
    }

    @FunctionalInterface
    interface RetrySupplier<T, E extends Throwable> {
        T get() throws E;
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

    <E extends Throwable> void retry(RetryRunnable<E> runnable) throws RetryExhaustedException, E;

    <T, E extends Throwable> T retry(RetrySupplier<T, E> supplier) throws RetryExhaustedException, E;

    <T, E extends Throwable> T retry(RetrySupplier<T, E> supplier, RetrySupplier<T, E> fallback) throws E;

    <T> CompletionStage<T> retry(Supplier<CompletionStage<T>> supplier);
}
