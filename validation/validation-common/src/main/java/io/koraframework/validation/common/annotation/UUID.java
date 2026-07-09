package io.koraframework.validation.common.annotation;

import io.koraframework.common.annotation.AopAnnotation;
import io.koraframework.validation.common.constraint.factory.UuidValidatorFactory;

import java.lang.annotation.*;

/**
 * Validates that {@link String} or {@link CharSequence} is valid UUID
 */
@AopAnnotation
@Documented
@Retention(value = RetentionPolicy.CLASS)
@Target(value = {ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@ValidatedBy(UuidValidatorFactory.class)
public @interface UUID {

}
