package ru.tinkoff.kora.resilient.retry;

import javax.annotation.Nonnull;

/**
 * Exception that indicates all Retry attempts exhausted
 */
public final class RetryAttemptException extends RuntimeException {

    public RetryAttemptException(int attempts, @Nonnull Throwable cause) {
        super("All '" + attempts + "' retry attempts exhausted", cause);
    }
}
