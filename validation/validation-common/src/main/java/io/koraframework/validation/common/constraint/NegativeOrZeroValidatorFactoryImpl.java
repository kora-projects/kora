package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.NegativeOrZeroValidatorFactory;

public final class NegativeOrZeroValidatorFactoryImpl<T extends Number> implements NegativeOrZeroValidatorFactory<T> {
    @SuppressWarnings("rawtypes")
    private static final NegativeOrZeroValidatorFactoryImpl INSTANCE = new NegativeOrZeroValidatorFactoryImpl();

    @SuppressWarnings("unchecked")
    public static <T extends Number> NegativeOrZeroValidatorFactory<T> instance() {
        return (NegativeOrZeroValidatorFactory<T>) INSTANCE;
    }

    @Override
    public Validator<T> create() {
        return new NumberValidator<>(NumberValidator.Rule.NEGATIVE_OR_ZERO);
    }
}
