package ru.tinkoff.kora.resilient.circuitbreaker;

public final class CallNotPermittedException extends RuntimeException{

    public CallNotPermittedException(CircuitBreaker.State state, String name) {
        super("Call Is Not Permitted due to CircuitBreaker '" + name + "' been in " + state + " state");
    }
}
