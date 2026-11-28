package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.PositiveValidatorFactory;

public final class PositiveValidatorFactoryImpl<T extends Number> implements PositiveValidatorFactory<T> {
    @SuppressWarnings("rawtypes")
    private static final PositiveValidatorFactoryImpl INSTANCE = new PositiveValidatorFactoryImpl();

    @SuppressWarnings("unchecked")
    public static <T extends Number> PositiveValidatorFactory<T> instance() {
        return (PositiveValidatorFactory<T>) INSTANCE;
    }

    @Override
    public Validator<T> create() {
        return new NumberValidator<>(NumberValidator.Rule.POSITIVE);
    }
}
