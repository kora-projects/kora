package io.koraframework.nats.common.exceptions;

public class NatsPublishException extends RuntimeException {
    public NatsPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
