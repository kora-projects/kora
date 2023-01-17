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
    interface RetryState {

        boolean canRetry(@Nonnull Throwable throwable);

        void checkRetry(@Nonnull Throwable throwable) throws RetryException;

        long getDelayNanos();

        void doDelay();
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

    void retry(@Nonnull Runnable runnable) throws RetryException;

    <T> T retry(@Nonnull Supplier<T> supplier) throws RetryException;

    <T> T retry(@Nonnull Supplier<T> supplier, Supplier<T> fallback) throws RetryException;
}
