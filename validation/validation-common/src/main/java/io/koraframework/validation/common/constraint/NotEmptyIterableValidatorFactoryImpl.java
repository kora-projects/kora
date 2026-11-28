package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.NotEmptyValidatorFactory;

public final class NotEmptyIterableValidatorFactoryImpl<T> implements NotEmptyValidatorFactory<Iterable<T>> {
    @SuppressWarnings("rawtypes")
    private static final NotEmptyIterableValidatorFactoryImpl INSTANCE = new NotEmptyIterableValidatorFactoryImpl();

    @SuppressWarnings("unchecked")
    public static <T, C extends Iterable<T>> NotEmptyValidatorFactory<C> instance() {
        return (NotEmptyValidatorFactory<C>) INSTANCE;
    }

    @Override
    public Validator<Iterable<T>> create() {
        return new NotEmptyIterableValidator<>();
    }
}
