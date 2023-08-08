package ru.tinkoff.kora.validation.common.constraint.factory;

import ru.tinkoff.kora.validation.common.Validator;
import ru.tinkoff.kora.validation.common.ValidatorFactory;

import javax.annotation.Nonnull;

public interface PatternValidatorFactory<T> extends ValidatorFactory<T> {

    @Nonnull
    @Override
    default Validator<T> create() {
        throw new UnsupportedOperationException("Doesn't support creation without Pattern!");
    }

    @Nonnull
    default Validator<T> create(String pattern) {
        return create(pattern, 0);
    }

    @Nonnull
    Validator<T> create(String pattern, int flags);
}
