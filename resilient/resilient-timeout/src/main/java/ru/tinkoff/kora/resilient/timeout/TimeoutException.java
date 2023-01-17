package ru.tinkoff.kora.resilient.timeout;

public final class TimeoutException extends RuntimeException {

    public TimeoutException(String message) {
        super(message);
    }
}
