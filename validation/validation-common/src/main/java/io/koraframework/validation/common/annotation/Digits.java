package io.koraframework.validation.common.annotation;

import io.koraframework.common.annotation.AopAnnotation;
import io.koraframework.validation.common.constraint.factory.DigitsValidatorFactory;

import java.lang.annotation.*;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Validates that {@link Number}, {@link BigInteger}, {@link BigDecimal}, {@link String} or {@link CharSequence} has limited integer and fraction digits
 */
@AopAnnotation
@Documented
@Retention(value = RetentionPolicy.CLASS)
@Target(value = {ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@ValidatedBy(DigitsValidatorFactory.class)
public @interface Digits {

    int integer();

    int fraction();
}
