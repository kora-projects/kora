package io.koraframework.validation.common.annotation;

import io.koraframework.common.annotation.AopAnnotation;
import io.koraframework.validation.common.constraint.factory.AssertFalseValidatorFactory;

import java.lang.annotation.*;

/**
 * Validates that {@link Boolean} value is false
 */
@AopAnnotation
@Documented
@Retention(value = RetentionPolicy.CLASS)
@Target(value = {ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@ValidatedBy(AssertFalseValidatorFactory.class)
public @interface AssertFalse {

}
