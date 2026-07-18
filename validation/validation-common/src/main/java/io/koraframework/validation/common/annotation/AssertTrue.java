package io.koraframework.validation.common.annotation;

import io.koraframework.common.annotation.AopAnnotation;
import io.koraframework.validation.common.constraint.factory.AssertTrueValidatorFactory;

import java.lang.annotation.*;

/**
 * Validates that {@link Boolean} value is true
 */
@AopAnnotation
@Documented
@Retention(value = RetentionPolicy.CLASS)
@Target(value = {ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@ValidatedBy(AssertTrueValidatorFactory.class)
public @interface AssertTrue {

}
