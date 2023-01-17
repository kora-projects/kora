package ru.tinkoff.kora.resilient.circuitbreaker;

public final class CallFallbackException extends CallException {

    public CallFallbackException(Throwable throwable, String name) {
        super(throwable, name);
    }
}
