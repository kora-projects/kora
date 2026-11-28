package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.MaxValidatorFactory;

public final class MaxValidatorFactoryImpl<T extends Number> implements MaxValidatorFactory<T> {
    @SuppressWarnings("rawtypes")
    private static final MaxValidatorFactoryImpl INSTANCE = new MaxValidatorFactoryImpl();

    @SuppressWarnings("unchecked")
    public static <T extends Number> MaxValidatorFactory<T> instance() {
        return (MaxValidatorFactory<T>) INSTANCE;
    }

    @Override
    public Validator<T> create(long value) {
        return new NumberValidator<>(NumberValidator.Rule.MAX, value);
    }
}
