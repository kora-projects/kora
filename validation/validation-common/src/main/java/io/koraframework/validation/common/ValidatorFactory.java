package io.koraframework.validation.common;


/**
 * Factory that is responsible for creating new {@link Validator<T>} implementations
 */
@FunctionalInterface
public interface ValidatorFactory<T> {

    Validator<T> create();
}
