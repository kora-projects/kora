package ru.tinkoff.kora.resilient.circuitbreaker;

import ru.tinkoff.kora.resilient.ResilientException;

public final class CallNotPermittedException extends ResilientException {

    public CallNotPermittedException(CircuitBreaker.State state, String name) {
        super(name, "Call Is Not Permitted due to CircuitBreaker '" + name + "' been in " + state + " state");
    }
}
