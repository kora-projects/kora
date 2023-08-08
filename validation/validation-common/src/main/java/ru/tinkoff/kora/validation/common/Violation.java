package ru.tinkoff.kora.validation.common;

import ru.tinkoff.kora.validation.common.ValidationContext.Path;

import javax.annotation.Nonnull;

/**
 * Indicates validation failure
 */
public interface Violation {

    /**
     * @return failure message
     */
    @Nonnull
    String message();

    /**
     * @return path for value where failure occurred
     */
    @Nonnull
    Path path();
}
