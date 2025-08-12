package ru.tinkoff.kora.resilient.circuitbreaker;

import ru.tinkoff.kora.resilient.ResilientException;

public final class CallNotPermittedException extends ResilientException {

    private final CircuitBreaker.State state;

    public CallNotPermittedException(CircuitBreaker.State state, String name) {
        super(name, (state == CircuitBreaker.State.OPEN)
            ? "Call Is Not Permitted due to CircuitBreaker '" + name + "' been in " + state + " state"
            : "Call Is Not Permitted due to CircuitBreaker '" + name + "' been in " + state + " state and all permitted calls already acquired");
        this.state = state;
    }

    public CircuitBreaker.State state() {
        return state;
    }
}
