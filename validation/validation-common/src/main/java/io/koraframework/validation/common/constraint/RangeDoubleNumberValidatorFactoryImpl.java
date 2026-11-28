package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.annotation.Range;
import io.koraframework.validation.common.constraint.factory.RangeValidatorFactory;

public final class RangeDoubleNumberValidatorFactoryImpl<T extends Number> implements RangeValidatorFactory<T> {
    @SuppressWarnings("rawtypes")
    private static final RangeDoubleNumberValidatorFactoryImpl INSTANCE = new RangeDoubleNumberValidatorFactoryImpl();

    @SuppressWarnings("unchecked")
    public static <T extends Number> RangeValidatorFactory<T> instance() {
        return (RangeValidatorFactory<T>) INSTANCE;
    }

    @Override
    public Validator<T> create(double from, double to, Range.Boundary boundary) {
        return new RangeDoubleNumberValidator<>(from, to, boundary);
    }
}
