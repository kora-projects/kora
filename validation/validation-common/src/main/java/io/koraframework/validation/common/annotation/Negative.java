package io.koraframework.validation.common.annotation;

import io.koraframework.common.annotation.AopAnnotation;
import io.koraframework.validation.common.constraint.factory.NegativeValidatorFactory;

import java.lang.annotation.*;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Validates {@link Number}, {@link BigInteger}, {@link BigDecimal} value to be less than zero
 */
@AopAnnotation
@Documented
@Retention(value = RetentionPolicy.CLASS)
@Target(value = {ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@ValidatedBy(NegativeValidatorFactory.class)
public @interface Negative {

}
