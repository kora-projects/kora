package io.koraframework.validation.common.annotation;

import io.koraframework.common.annotation.AopAnnotation;
import io.koraframework.validation.common.constraint.factory.UrlValidatorFactory;

import java.lang.annotation.*;

/**
 * Validates that {@link String} or {@link CharSequence} is absolute URL with scheme and host
 */
@AopAnnotation
@Documented
@Retention(value = RetentionPolicy.CLASS)
@Target(value = {ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@ValidatedBy(UrlValidatorFactory.class)
public @interface Url {

}
