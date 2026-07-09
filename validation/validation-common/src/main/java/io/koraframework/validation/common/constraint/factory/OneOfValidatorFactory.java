package io.koraframework.validation.common.constraint.factory;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.ValidatorFactory;

public interface OneOfValidatorFactory<T> extends ValidatorFactory<T> {

    @Override
    default Validator<T> create() {
        return create(new String[0]);
    }

    Validator<T> create(String[] value);
}
