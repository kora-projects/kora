package io.koraframework.validation.common.annotation;

import io.koraframework.common.annotation.AopAnnotation;
import io.koraframework.validation.common.constraint.factory.UriValidatorFactory;

import java.lang.annotation.*;

/**
 * Validates that {@link String} or {@link CharSequence} is valid URI
 */
@AopAnnotation
@Documented
@Retention(value = RetentionPolicy.CLASS)
@Target(value = {ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@ValidatedBy(UriValidatorFactory.class)
public @interface Uri {

}
