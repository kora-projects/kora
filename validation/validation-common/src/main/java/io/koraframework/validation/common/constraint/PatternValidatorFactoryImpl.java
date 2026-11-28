package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.PatternValidatorFactory;

public final class PatternValidatorFactoryImpl<T extends CharSequence> implements PatternValidatorFactory<T> {
    @SuppressWarnings("rawtypes")
    private static final PatternValidatorFactoryImpl INSTANCE = new PatternValidatorFactoryImpl();

    @SuppressWarnings("unchecked")
    public static <T extends CharSequence> PatternValidatorFactory<T> instance() {
        return (PatternValidatorFactory<T>) INSTANCE;
    }

    @Override
    public Validator<T> create(String pattern, int flags) {
        return new PatternValidator<>(pattern, flags);
    }
}
