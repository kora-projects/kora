package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.constraint.factory.*;

import java.util.Map;

public final class NotEmptyMapValidatorFactoryImpl<K, V> implements NotEmptyValidatorFactory<Map<K, V>> {
    @SuppressWarnings("rawtypes")
    private static final NotEmptyMapValidatorFactoryImpl INSTANCE = new NotEmptyMapValidatorFactoryImpl();

    @SuppressWarnings("unchecked")
    public static <K, V> NotEmptyValidatorFactory<Map<K, V>> instance() {
        return (NotEmptyValidatorFactory<Map<K, V>>) INSTANCE;
    }

    @Override
    public Validator<Map<K, V>> create() {
        return new NotEmptyMapValidator<>();
    }
}
