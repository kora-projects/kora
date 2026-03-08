package io.koraframework.resilient.retry;

import io.koraframework.resilient.ResilientException;

/**
 * Exception that indicates all Retry attempts exhausted
 */
public final class RetryExhaustedException extends ResilientException {

    public RetryExhaustedException(String name, int attempts, Throwable cause) {
        super(name, "All '" + attempts + "' retry attempts exhausted: " + cause.getMessage(), cause);
    }
}
