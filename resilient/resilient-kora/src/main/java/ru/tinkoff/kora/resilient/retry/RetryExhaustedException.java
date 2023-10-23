package ru.tinkoff.kora.resilient.retry;

import jakarta.annotation.Nonnull;
import ru.tinkoff.kora.resilient.ResilientException;

/**
 * Exception that indicates all Retry attempts exhausted
 */
public final class RetryExhaustedException extends ResilientException {

    public RetryExhaustedException(int attempts, @Nonnull Throwable cause) {
        super("All '" + attempts + "' retry attempts exhausted", cause);
    }
}
