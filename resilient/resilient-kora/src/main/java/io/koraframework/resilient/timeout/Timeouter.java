package io.koraframework.resilient.timeout;

import io.koraframework.resilient.common.ThrowableCallable;
import io.koraframework.resilient.common.ThrowableRunnable;
import io.koraframework.resilient.timeout.exception.TimeoutExhaustedException;

import java.time.Duration;

/**
 * Timeout executor contract
 */
public interface Timeouter {

    /**
     * @return duration timeout executor is configured for
     */
    Duration timeout();

    /**
     * @return false when timeout is disabled and calls should be executed without time limit
     */
    default boolean enabled() {
        return true;
    }

    /**
     * @param runnable to execute
     * @throws TimeoutExhaustedException when timed out
     */
    <E extends Throwable> void execute(ThrowableRunnable<E> runnable) throws E, TimeoutExhaustedException;

    /**
     * @param callable to execute
     * @throws TimeoutExhaustedException when timed out
     */
    <T, E extends Throwable> T execute(ThrowableCallable<T, E> callable) throws E, TimeoutExhaustedException;
}
