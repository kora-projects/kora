package ru.tinkoff.kora.validation.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

record SimpleViolation(String message, ValidationContext.Path path) implements Violation {

    static final Logger logger = LoggerFactory.getLogger(Validator.class);

    @Override
    public String toString() {
        return "Path=" + path + ", Message=" + message;
    }
}
