package ru.tinkoff.kora.validation.common.annotation;

import ru.tinkoff.kora.validation.common.constraint.factory.RangeValidatorFactory;

import java.lang.annotation.*;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Validates {@link Integer}, {@link Long}, {@link Short}, {@link Float}, {@link Double}, {@link BigInteger}, {@link BigDecimal} value to be in specified range
 */
@Documented
@Retention(value = RetentionPolicy.CLASS)
@Target(value = {ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@ValidatedBy(RangeValidatorFactory.class)
public @interface Range {

    /**
     * @return minimum value should have (boundary rule {@link #boundary()})
     */
    double from();

    /**
     * @return maximum value should have (boundary rule {@link #boundary()})
     */
    double to();

    enum Boundary {
        EXCLUSIVE_EXCLUSIVE,
        INCLUSIVE_EXCLUSIVE,
        EXCLUSIVE_INCLUSIVE,
        INCLUSIVE_INCLUSIVE,
    }

    /**
     * @return boundary to apply when validating corner cases for value
     */
    Boundary boundary() default Boundary.INCLUSIVE_INCLUSIVE;
}
