package io.koraframework.resilient.timeout;

import io.koraframework.resilient.timeout.exception.TimeoutExhaustedException;

import java.time.Duration;
import java.util.concurrent.Callable;

/**
 * Timeout executor contract
 */
public interface Timeouter {

    /**
     * @return duration timeout executor is configured for
     */
    Duration timeout();

    /**
     * @param runnable to execute
     * @throws TimeoutExhaustedException when timed out
     */
    <E extends Throwable> void execute(TimeoutRunnable<E> runnable) throws E, TimeoutExhaustedException;

    /**
     * @param callable to execute
     * @throws TimeoutExhaustedException when timed out
     */
    <T, E extends Throwable> T execute(TimeoutCallable<T, E> callable) throws E, TimeoutExhaustedException;

    @FunctionalInterface
    interface TimeoutRunnable<E extends Throwable> {

        void run() throws E;
    }

    @FunctionalInterface
    interface TimeoutCallable<T, E extends Throwable> {

        T call() throws E;
    }
}
