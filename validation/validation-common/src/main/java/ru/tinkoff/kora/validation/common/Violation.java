package ru.tinkoff.kora.validation.common;

import ru.tinkoff.kora.validation.common.ValidationContext.Path;


/**
 * Indicates validation failure
 */
public interface Violation {

    /**
     * @return failure message
     */
    String message();

    /**
     * @return path for value where failure occurred
     */
    Path path();
}
