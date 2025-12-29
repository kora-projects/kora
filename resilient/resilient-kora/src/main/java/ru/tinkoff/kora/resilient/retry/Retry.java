package ru.tinkoff.kora.resilient.retry;

import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Retry executor implementation
 */
public interface Retry {

    @FunctionalInterface
    interface RetryRunnable<E extends Throwable> {

        void run() throws E;
    }

    @FunctionalInterface
    interface RetrySupplier<T, E extends Throwable> {

        T get() throws E;
    }

    /**
     * Retry State implementation for manual retry execution handling
     */
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

    /**
     * @return new {@link RetryState}
     */
    RetryState asState();

    /**
     * @param runnable to execute for successful completion
     * @throws RetryExhaustedException if exhausted all attempts
     */
    <E extends Throwable> void retry(RetryRunnable<E> runnable) throws RetryExhaustedException, E;

    /**
     * @param supplier to use for value extraction
     * @param <T>      type of value
     * @return value is succeeded
     * @throws RetryExhaustedException if exhausted all attempts
     */
    <T, E extends Throwable> T retry(RetrySupplier<T, E> supplier) throws RetryExhaustedException, E;

    /**
     * @param supplier to use for value extraction
     * @param fallback to use for value if failed to retrieve value from supplier
     * @param <T>      type of value
     * @return value is succeeded
     */
    <T, E extends Throwable> T retry(RetrySupplier<T, E> supplier, RetrySupplier<T, E> fallback) throws E;

    /**
     * @param supplier to use for value extraction
     * @param <T>      type of value
     * @return value is succeeded
     */
    <T> CompletionStage<T> retry(Supplier<CompletionStage<T>> supplier);
}
