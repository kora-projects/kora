package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.annotation.Range;
import io.koraframework.validation.common.constraint.factory.RangeValidatorFactory;
import java.math.BigDecimal;

public final class RangeBigDecimalValidatorFactoryImpl implements RangeValidatorFactory<BigDecimal> {
    public static final RangeBigDecimalValidatorFactoryImpl INSTANCE = new RangeBigDecimalValidatorFactoryImpl();

    @Override
    public Validator<BigDecimal> create(double from, double to, Range.Boundary boundary) {
        return new RangeBigDecimalValidator(from, to, boundary);
    }
}
