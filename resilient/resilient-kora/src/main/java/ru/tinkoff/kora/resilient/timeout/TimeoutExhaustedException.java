package ru.tinkoff.kora.resilient.timeout;

import ru.tinkoff.kora.resilient.ResilientException;

public final class TimeoutExhaustedException extends ResilientException {

    public TimeoutExhaustedException(String name, String message) {
        super(name, message);
    }
}
