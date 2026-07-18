package io.koraframework.validation.common.annotation;

import io.koraframework.common.annotation.AopAnnotation;
import io.koraframework.validation.common.constraint.factory.OneOfValidatorFactory;

import java.lang.annotation.*;

/**
 * Validates that {@link String} or {@link CharSequence} is one of specified values
 */
@AopAnnotation
@Documented
@Retention(value = RetentionPolicy.CLASS)
@Target(value = {ElementType.METHOD, ElementType.FIELD, ElementType.PARAMETER})
@ValidatedBy(OneOfValidatorFactory.class)
public @interface OneOf {

    String[] value();
}
