package io.koraframework.resilient.timeout.exception;

import io.koraframework.resilient.exception.ResilientException;

public final class TimeoutExhaustedException extends ResilientException {

    public TimeoutExhaustedException(String name, String message) {
        super(name, message);
    }
}
