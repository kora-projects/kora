package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.UuidValidatorFactory;

public final class UuidValidatorFactoryImpl<T extends CharSequence> implements UuidValidatorFactory<T> {
    @SuppressWarnings("rawtypes")
    private static final UuidValidatorFactoryImpl INSTANCE = new UuidValidatorFactoryImpl();

    @SuppressWarnings("unchecked")
    public static <T extends CharSequence> UuidValidatorFactory<T> instance() {
        return (UuidValidatorFactory<T>) INSTANCE;
    }

    @Override
    public Validator<T> create() {
        return new UuidValidator<>();
    }
}
