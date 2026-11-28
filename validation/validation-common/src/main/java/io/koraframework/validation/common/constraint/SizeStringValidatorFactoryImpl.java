package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.SizeValidatorFactory;

public final class SizeStringValidatorFactoryImpl<T extends CharSequence> implements SizeValidatorFactory<T> {
    @SuppressWarnings("rawtypes")
    private static final SizeStringValidatorFactoryImpl INSTANCE = new SizeStringValidatorFactoryImpl();

    @SuppressWarnings("unchecked")
    public static <T extends CharSequence> SizeValidatorFactory<T> instance() {
        return (SizeValidatorFactory<T>) INSTANCE;
    }

    @Override
    public Validator<T> create(int from, int to) {
        return new SizeStringValidator<>(from, to);
    }
}
