package ru.tinkoff.kora.resilient.retry;

public class RetryException extends RuntimeException {

    public RetryException(String message) {
        super(message);
    }

    public RetryException(Throwable cause) {
        super(cause);
    }
}
