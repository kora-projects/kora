package ru.tinkoff.kora.validation.common.constraint.factory;

import ru.tinkoff.kora.validation.common.Validator;
import ru.tinkoff.kora.validation.common.ValidatorFactory;


public interface PatternValidatorFactory<T> extends ValidatorFactory<T> {

    @Override
    default Validator<T> create() {
        throw new UnsupportedOperationException("Doesn't support creation without Pattern!");
    }

    default Validator<T> create(String pattern) {
        return create(pattern, 0);
    }

    Validator<T> create(String pattern, int flags);
}
