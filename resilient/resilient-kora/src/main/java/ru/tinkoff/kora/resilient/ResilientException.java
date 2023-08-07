package ru.tinkoff.kora.resilient;

public class ResilientException extends RuntimeException {

    private final String name;

    public ResilientException(String name, String message) {
        super(message);
        this.name = name;
    }

    public ResilientException(String name, Throwable cause) {
        super(cause);
        this.name = name;
    }

    public String name() {
        return name;
    }
}
