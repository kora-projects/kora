package ru.tinkoff.kora.validation.common;

import javax.annotation.Nonnull;
import ru.tinkoff.kora.validation.common.ValidationContext.Path;

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
