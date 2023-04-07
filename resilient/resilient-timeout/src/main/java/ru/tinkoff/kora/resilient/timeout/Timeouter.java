package ru.tinkoff.kora.resilient.timeout;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * Timeout executor contract
 */
public interface Timeouter {

    /**
     * @return duration timeout executor is configured for
     */
    @Nonnull
    Duration timeout();

    /**
     * @param runnable to execute
     * @throws TimeoutException when timed out
     */
    void execute(@Nonnull Runnable runnable) throws TimeoutException;

    /**
     * @param supplier to execute
     * @throws TimeoutException when timed out
     */
    <T> T execute(@Nonnull Callable<T> supplier) throws TimeoutException;
}
