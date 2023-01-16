package ru.tinkoff.kora.validation.common;

record SimpleViolation(String message, ValidationContext.Path path) implements Violation {

    @Override
    public String toString() {
        return "Path=" + path + ", Message=" + message;
    }
}
