package io.koraframework.resilient.circuitbreaker.exception;

import io.koraframework.resilient.exception.ResilientException;
import io.koraframework.resilient.circuitbreaker.CircuitBreaker;

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
