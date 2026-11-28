package io.koraframework.validation.common.constraint;

import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.annotation.Range;
import io.koraframework.validation.common.constraint.factory.RangeValidatorFactory;
import java.math.BigInteger;

public final class RangeBigIntegerValidatorFactoryImpl implements RangeValidatorFactory<BigInteger> {
    public static final RangeBigIntegerValidatorFactoryImpl INSTANCE = new RangeBigIntegerValidatorFactoryImpl();

    @Override
    public Validator<BigInteger> create(double from, double to, Range.Boundary boundary) {
        return new RangeBigIntegerValidator(from, to, boundary);
    }
}
