package io.koraframework.resilient.timeout;

import io.koraframework.resilient.ResilientException;

public final class TimeoutExhaustedException extends ResilientException {

    public TimeoutExhaustedException(String name, String message) {
        super(name, message);
    }
}
