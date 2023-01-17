package ru.tinkoff.kora.resilient.fallback;

public final class FallbackException extends RuntimeException {

    private final String name;

    public FallbackException(Throwable throwable, String name) {
        super(throwable.getMessage(), throwable);
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
