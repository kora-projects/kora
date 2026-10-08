package io.koraframework.resilient.bulkhead.annotation;

import io.koraframework.common.annotation.AopAnnotation;

import java.lang.annotation.*;

@AopAnnotation
@Documented
@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = {
        ElementType.METHOD
})
public @interface Bulkheaded {

    /**
     * @return Bulkhead implementation interface
     */
    Class<? extends io.koraframework.resilient.bulkhead.Bulkhead> value();
}
