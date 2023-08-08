package ru.tinkoff.kora.validation.common.constraint.factory;

import ru.tinkoff.kora.validation.common.Validator;
import ru.tinkoff.kora.validation.common.ValidatorFactory;

import javax.annotation.Nonnull;

public interface SizeValidatorFactory<T> extends ValidatorFactory<T> {

    @Nonnull
    @Override
    default Validator<T> create() {
        return create(0, Integer.MAX_VALUE);
    }

    @Nonnull
    default Validator<T> create(int to) {
        return create(0, to);
    }

    @Nonnull
    Validator<T> create(int from, int to);
}
