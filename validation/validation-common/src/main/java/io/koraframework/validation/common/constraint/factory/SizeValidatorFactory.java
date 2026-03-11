package io.koraframework.validation.common.constraint.factory;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.ValidatorFactory;


public interface SizeValidatorFactory<T> extends ValidatorFactory<T> {

    @Override
    default Validator<T> create() {
        return create(0, Integer.MAX_VALUE);
    }

    default Validator<T> create(int to) {
        return create(0, to);
    }

    Validator<T> create(int from, int to);
}
