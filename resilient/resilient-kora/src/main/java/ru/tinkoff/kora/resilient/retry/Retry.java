package ru.tinkoff.kora.resilient.retry;

import jakarta.annotation.Nonnull;

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

        @Nonnull
        RetryStatus onException(@Nonnull Throwable throwable);

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
    @Nonnull
    RetryState asState();

    /**
     * @param runnable to execute for successful completion
     * @throws RetryExhaustedException if exhausted all attempts
     */
    <E extends Throwable> void retry(@Nonnull RetryRunnable<E> runnable) throws RetryExhaustedException, E;

    /**
     * @param supplier to use for value extraction
     * @param <T>      type of value
     * @return value is succeeded
     * @throws RetryExhaustedException if exhausted all attempts
     */
    <T, E extends Throwable> T retry(@Nonnull RetrySupplier<T, E> supplier) throws RetryExhaustedException, E;

    /**
     * @param supplier to use for value extraction
     * @param fallback to use for value if failed to retrieve value from supplier
     * @param <T>      type of value
     * @return value is succeeded
     */
    <T, E extends Throwable> T retry(@Nonnull RetrySupplier<T, E> supplier, RetrySupplier<T, E> fallback) throws E;

    /**
     * @param supplier to use for value extraction
     * @param <T>      type of value
     * @return value is succeeded
     */
    <T> CompletionStage<T> retry(@Nonnull Supplier<CompletionStage<T>> supplier);
}
