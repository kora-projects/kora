package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.NegativeValidatorFactory;

public final class NegativeValidatorFactoryImpl<T extends Number> implements NegativeValidatorFactory<T> {
    @SuppressWarnings("rawtypes")
    private static final NegativeValidatorFactoryImpl INSTANCE = new NegativeValidatorFactoryImpl();

    @SuppressWarnings("unchecked")
    public static <T extends Number> NegativeValidatorFactory<T> instance() {
        return (NegativeValidatorFactory<T>) INSTANCE;
    }

    @Override
    public Validator<T> create() {
        return new NumberValidator<>(NumberValidator.Rule.NEGATIVE);
    }
}
