package io.koraframework.nats.common.exceptions;

public class NatsSerializationException extends RuntimeException {
    public NatsSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
