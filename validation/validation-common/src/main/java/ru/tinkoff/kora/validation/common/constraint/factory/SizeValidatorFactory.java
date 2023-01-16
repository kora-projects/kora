package ru.tinkoff.kora.validation.common.constraint.factory;

import javax.annotation.Nonnull;
import ru.tinkoff.kora.validation.common.Validator;
import ru.tinkoff.kora.validation.common.ValidatorFactory;

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
