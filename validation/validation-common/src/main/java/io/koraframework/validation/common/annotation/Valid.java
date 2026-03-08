package io.koraframework.validation.common.annotation;

import io.koraframework.common.AopAnnotation;
import io.koraframework.validation.common.Validator;

import java.lang.annotation.*;

/**
 * Indicates that Type Field / Method Argument / Return Value should be validated and thus such {@link Validator} will be expected
 * <p>
 * When Class/Record is annotated then {@link Validator} for such type will be generated
 */
@AopAnnotation
@Documented
@Retention(value = RetentionPolicy.CLASS)
@Target(value = {ElementType.METHOD, ElementType.TYPE, ElementType.FIELD, ElementType.PARAMETER})
public @interface Valid {

}
