package io.koraframework.validation.common.annotation;

import io.koraframework.common.annotation.AopAnnotation;
import io.koraframework.validation.common.constraint.factory.PositiveValidatorFactory;

import java.lang.annotation.*;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Validates {@link Number}, {@link BigInteger}, {@link BigDecimal} value to be greater than zero
 */
@AopAnnotation
@Documented
@Retention(value = RetentionPolicy.CLASS)
@Target(value = {ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@ValidatedBy(PositiveValidatorFactory.class)
public @interface Positive {

}
