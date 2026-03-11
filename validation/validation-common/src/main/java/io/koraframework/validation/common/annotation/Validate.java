package io.koraframework.validation.common.annotation;

import io.koraframework.common.AopAnnotation;
import io.koraframework.validation.common.ValidationContext;

import java.lang.annotation.*;

/**
 * Indicates that Method Arguments / Method Return Value should be validated
 */
@AopAnnotation
@Documented
@Retention(value = RetentionPolicy.CLASS)
@Target(value = {ElementType.METHOD})
public @interface Validate {

    /**
     * @return true if Fail Fast
     * @see ValidationContext#isFailFast()
     */
    boolean failFast() default false;
}
