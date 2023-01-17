package ru.tinkoff.kora.resilient.circuitbreaker;

public class CallException extends RuntimeException {

    /**
     * {@link CircuitBreaker} name
     */
    private final String name;

    public CallException(Throwable throwable, String name) {
        super(throwable.getMessage(), throwable);
        this.name = name;
    }

    CallException(String message, String name) {
        super(message);
        this.name = name;
    }

    public String getCircuitBreaker() {
        return name;
    }
}
