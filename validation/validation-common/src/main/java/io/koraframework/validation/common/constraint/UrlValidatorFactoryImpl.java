package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.UrlValidatorFactory;

public final class UrlValidatorFactoryImpl<T extends CharSequence> implements UrlValidatorFactory<T> {
    @SuppressWarnings("rawtypes")
    private static final UrlValidatorFactoryImpl INSTANCE = new UrlValidatorFactoryImpl();

    @SuppressWarnings("unchecked")
    public static <T extends CharSequence> UrlValidatorFactory<T> instance() {
        return (UrlValidatorFactory<T>) INSTANCE;
    }

    @Override
    public Validator<T> create() {
        return new UrlValidator<>();
    }
}
