package ru.tinkoff.kora.resilient.timeout;

import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * Timeout executor contract
 */
public interface Timeout {

    /**
     * @return duration timeout executor is configured for
     */
    Duration timeout();

    /**
     * @param runnable to execute
     * @throws TimeoutExhaustedException when timed out
     */
    void execute(Runnable runnable) throws TimeoutExhaustedException;

    /**
     * @param supplier to execute
     * @throws TimeoutExhaustedException when timed out
     */
    <T> T execute(Callable<T> supplier) throws TimeoutExhaustedException;
}
