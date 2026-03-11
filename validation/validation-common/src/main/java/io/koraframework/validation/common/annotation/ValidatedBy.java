package io.koraframework.validation.common.annotation;

import io.koraframework.common.AopAnnotation;
import io.koraframework.validation.common.Validator;
import io.koraframework.validation.common.ValidatorFactory;

import java.lang.annotation.*;

/**
 * Indicates that annotation is used for validation and providers factory that instantiates {@link Validator}
 */
@AopAnnotation
@Documented
@Retention(value = RetentionPolicy.CLASS)
@Target(value = {ElementType.ANNOTATION_TYPE})
public @interface ValidatedBy {

    @SuppressWarnings("rawtypes")
    Class<? extends ValidatorFactory> value();
}
