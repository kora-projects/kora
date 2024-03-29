package ru.tinkoff.kora.validation.common;

import jakarta.annotation.Nonnull;

/**
 * Factory that is responsible for creating new {@link Validator<T>} implementations
 */
@FunctionalInterface
public interface ValidatorFactory<T> {

    @Nonnull
    Validator<T> create();
}
