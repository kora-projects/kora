package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.UriValidatorFactory;

public final class UriValidatorFactoryImpl<T extends CharSequence> implements UriValidatorFactory<T> {
    @SuppressWarnings("rawtypes")
    private static final UriValidatorFactoryImpl INSTANCE = new UriValidatorFactoryImpl();

    @SuppressWarnings("unchecked")
    public static <T extends CharSequence> UriValidatorFactory<T> instance() {
        return (UriValidatorFactory<T>) INSTANCE;
    }

    @Override
    public Validator<T> create() {
        return new UriValidator<>();
    }
}
