package ru.tinkoff.kora.resilient.retry;

import jakarta.annotation.Nonnull;
import ru.tinkoff.kora.resilient.ResilientException;

/**
 * Exception that indicates all Retry attempts exhausted
 */
public final class RetryExhaustedException extends ResilientException {

    @Deprecated
    public RetryExhaustedException(int attempts, @Nonnull Throwable cause) {
        super("unknown", "All '" + attempts + "' retry attempts exhausted: " + cause.getMessage(), cause);
    }

    public RetryExhaustedException(String name, int attempts, @Nonnull Throwable cause) {
        super(name, "All '" + attempts + "' retry attempts exhausted: " + cause.getMessage(), cause);
    }
}
