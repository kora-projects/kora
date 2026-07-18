package io.koraframework.validation.common.constraint.factory;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.ValidatorFactory;

public interface DigitsValidatorFactory<T> extends ValidatorFactory<T> {

    @Override
    default Validator<T> create() {
        return create(Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    Validator<T> create(int integer, int fraction);
}
