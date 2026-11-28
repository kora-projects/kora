package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.DigitsValidatorFactory;

public final class DigitsValidatorFactoryImpl<T> implements DigitsValidatorFactory<T> {
    @SuppressWarnings("rawtypes")
    private static final DigitsValidatorFactoryImpl INSTANCE = new DigitsValidatorFactoryImpl();

    @SuppressWarnings("unchecked")
    public static <T> DigitsValidatorFactory<T> instance() {
        return (DigitsValidatorFactory<T>) INSTANCE;
    }

    @Override
    public Validator<T> create(int integer, int fraction) {
        return new DigitsValidator<>(integer, fraction);
    }
}
