package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.OneOfValidatorFactory;

public final class OneOfValidatorFactoryImpl<T extends CharSequence> implements OneOfValidatorFactory<T> {
    @SuppressWarnings("rawtypes")
    private static final OneOfValidatorFactoryImpl INSTANCE = new OneOfValidatorFactoryImpl();

    @SuppressWarnings("unchecked")
    public static <T extends CharSequence> OneOfValidatorFactory<T> instance() {
        return (OneOfValidatorFactory<T>) INSTANCE;
    }

    @Override
    public Validator<T> create(String[] values) {
        return new OneOfValidator<>(values);
    }
}
