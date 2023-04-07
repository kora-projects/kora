package ru.tinkoff.kora.resilient.retry;

/**
 * Exception that indicates all Retry attempts exhausted
 */
public final class RetryAttemptException extends RuntimeException {

    public RetryAttemptException(int attempts) {
        super("All '" + attempts + "' attempts exhausted during retry");
    }
}
