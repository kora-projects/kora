package io.koraframework.validation.common.annotation;

import io.koraframework.common.annotation.AopAnnotation;
import io.koraframework.validation.common.constraint.factory.NegativeOrZeroValidatorFactory;

import java.lang.annotation.*;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Validates {@link Number}, {@link BigInteger}, {@link BigDecimal} value to be less than or equal to zero
 */
@AopAnnotation
@Documented
@Retention(value = RetentionPolicy.CLASS)
@Target(value = {ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@ValidatedBy(NegativeOrZeroValidatorFactory.class)
public @interface NegativeOrZero {

}
