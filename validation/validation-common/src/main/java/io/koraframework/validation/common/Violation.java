package io.koraframework.validation.common;

import io.koraframework.validation.common.ValidationContext.Path;


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
