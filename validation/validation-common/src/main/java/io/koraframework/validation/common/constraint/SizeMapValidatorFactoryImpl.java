package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.SizeValidatorFactory;
import java.util.Map;

public final class SizeMapValidatorFactoryImpl<K, V> implements SizeValidatorFactory<Map<K, V>> {
    @SuppressWarnings("rawtypes")
    private static final SizeMapValidatorFactoryImpl INSTANCE = new SizeMapValidatorFactoryImpl();

    @SuppressWarnings("unchecked")
    public static <K, V> SizeValidatorFactory<Map<K, V>> instance() {
        return (SizeValidatorFactory<Map<K, V>>) INSTANCE;
    }

    @Override
    public Validator<Map<K, V>> create(int from, int to) {
        return new SizeMapValidator<>(from, to);
    }
}
