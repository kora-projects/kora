package ru.tinkoff.kora.resilient.circuitbreaker;

public final class CallNotPermittedException extends RuntimeException {

    public CallNotPermittedException(String message) {
        super(message);
    }
}
