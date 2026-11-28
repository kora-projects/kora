package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.PositiveOrZeroValidatorFactory;

public final class PositiveOrZeroValidatorFactoryImpl<T extends Number> implements PositiveOrZeroValidatorFactory<T> {
    @SuppressWarnings("rawtypes")
    private static final PositiveOrZeroValidatorFactoryImpl INSTANCE = new PositiveOrZeroValidatorFactoryImpl();

    @SuppressWarnings("unchecked")
    public static <T extends Number> PositiveOrZeroValidatorFactory<T> instance() {
        return (PositiveOrZeroValidatorFactory<T>) INSTANCE;
    }

    @Override
    public Validator<T> create() {
        return new NumberValidator<>(NumberValidator.Rule.POSITIVE_OR_ZERO);
    }
}
