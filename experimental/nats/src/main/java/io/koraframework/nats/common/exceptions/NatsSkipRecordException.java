package io.koraframework.nats.common.exceptions;

/**
 * Explicitly terminate a poison JetStream message instead of redelivering it.
 */
public class NatsSkipRecordException extends RuntimeException {
    public NatsSkipRecordException(String message) {
        super(message);
    }

    public NatsSkipRecordException(String message, Throwable cause) {
        super(message, cause);
    }
}
