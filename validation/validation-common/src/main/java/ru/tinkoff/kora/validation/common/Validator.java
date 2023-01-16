package ru.tinkoff.kora.validation.common;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.List;

/**
 * Validates instances
 *
 * @param <T> type of instance it supports
 */
public interface Validator<T> {

    /**
     * Validates value and return validation failures if occurred
     *
     * @param value   to validate
     * @param context context of validation and its options {@link ValidationContext}
     * @return validation violations
     */
    @Nonnull
    List<Violation> validate(@Nullable T value, @Nonnull ValidationContext context);

    /**
     * Validates value and return validation failures if occurred
     *
     * @param value to validate
     * @return validation violations
     */
    @Nonnull
    default List<Violation> validate(@Nullable T value) {
        return validate(value, new SimpleValidationContext(SimpleValidationContext.SimpleFieldPath.ROOT, false));
    }

    /**
     * Validates value and return validation failures if occurred
     *
     * @param value   to validate
     * @param context context of validation and its options {@link ValidationContext}
     * @throws ViolationException is thrown if any violations occur
     */
    default void validateAndThrow(@Nullable T value, @Nonnull ValidationContext context) throws ViolationException {
        final List<Violation> violations = validate(value, context);
        if (!violations.isEmpty()) {
            throw new ViolationException(violations);
        }
    }

    /**
     * Validates value and return validation failures if occurred
     *
     * @param value to validate
     * @throws ViolationException is thrown if any violations occur
     */
    default void validateAndThrow(@Nullable T value) throws ViolationException {
        final List<Violation> violations = validate(value);
        if (!violations.isEmpty()) {
            throw new ViolationException(violations);
        }
    }
}
