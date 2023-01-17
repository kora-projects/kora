package ru.tinkoff.kora.resilient.circuitbreaker;

public final class CallNotPermittedException extends CallException {

    public CallNotPermittedException(String message, String name) {
        super(message, name);
    }
}
