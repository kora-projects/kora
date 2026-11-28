package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.MinValidatorFactory;

public final class MinValidatorFactoryImpl<T extends Number> implements MinValidatorFactory<T> {
    @SuppressWarnings("rawtypes")
    private static final MinValidatorFactoryImpl INSTANCE = new MinValidatorFactoryImpl();

    @SuppressWarnings("unchecked")
    public static <T extends Number> MinValidatorFactory<T> instance() {
        return (MinValidatorFactory<T>) INSTANCE;
    }

    @Override
    public Validator<T> create(long value) {
        return new NumberValidator<>(NumberValidator.Rule.MIN, value);
    }
}
