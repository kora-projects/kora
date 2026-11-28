package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.SizeValidatorFactory;
import java.util.Collection;

public final class SizeCollectionValidatorFactoryImpl<V, T extends Collection<V>> implements SizeValidatorFactory<T> {
    @SuppressWarnings("rawtypes")
    private static final SizeCollectionValidatorFactoryImpl INSTANCE = new SizeCollectionValidatorFactoryImpl();

    @SuppressWarnings("unchecked")
    public static <V, T extends Collection<V>> SizeValidatorFactory<T> instance() {
        return (SizeValidatorFactory<T>) INSTANCE;
    }

    @Override
    public Validator<T> create(int from, int to) {
        return new SizeCollectionValidator<>(from, to);
    }
}
