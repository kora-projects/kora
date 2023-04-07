package ru.tinkoff.kora.resilient.retry;

import reactor.util.retry.Retry;

import javax.annotation.Nonnull;
import java.util.function.Supplier;

/**
 * Retry executor implementation
 */
public interface Retrier {

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
     * @return new {@link Retry} implementation for Project Reactor retry handling
     */
    @Nonnull
    Retry asReactor();

    void retry(@Nonnull Runnable runnable) throws RetryAttemptException;

    <T> T retry(@Nonnull Supplier<T> supplier) throws RetryAttemptException;

    <T> T retry(@Nonnull Supplier<T> supplier, Supplier<T> fallback) throws RetryAttemptException;
}
