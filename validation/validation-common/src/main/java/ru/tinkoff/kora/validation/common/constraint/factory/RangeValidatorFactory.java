package ru.tinkoff.kora.validation.common.constraint.factory;

import ru.tinkoff.kora.validation.common.Validator;
import ru.tinkoff.kora.validation.common.ValidatorFactory;
import ru.tinkoff.kora.validation.common.annotation.Range;


public interface RangeValidatorFactory<T> extends ValidatorFactory<T> {

    @Override
    default Validator<T> create() {
        return create(Double.MIN_VALUE, Double.MAX_VALUE, Range.Boundary.INCLUSIVE_INCLUSIVE);
    }

    default Validator<T> create(double from, double to) {
        return create(from, to, Range.Boundary.INCLUSIVE_INCLUSIVE);
    }

    Validator<T> create(double from, double to, Range.Boundary boundary);
}
