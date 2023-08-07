package ru.tinkoff.kora.resilient.retry;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

/**
 * Retry executor implementation
 */
public interface Retry {

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
     * @return new {@link reactor.util.retry.Retry} implementation for Project Reactor retry handling
     */
    @Nonnull
    reactor.util.retry.Retry asReactor();

    /**
     * @param runnable to execute for successful completion
     * @throws RetryExhaustedException if exhausted all attempts
     */
    void retry(@Nonnull Runnable runnable) throws RetryExhaustedException;

    /**
     * @param supplier to use for value extraction
     * @param <T>      type of value
     * @return value is succeeded
     * @throws RetryExhaustedException if exhausted all attempts
     */
    <T> T retry(@Nonnull Supplier<T> supplier) throws RetryExhaustedException;

    /**
     * @param supplier to use for value extraction
     * @param fallback to use for value if failed to retrieve value from supplier
     * @param <T>      type of value
     * @return value is succeeded
     */
    <T> T retry(@Nonnull Supplier<T> supplier, Supplier<T> fallback);
}
