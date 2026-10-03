package io.koraframework.resilient.fallback.annotation;

import io.koraframework.common.annotation.AopAnnotation;
import io.koraframework.common.annotation.AopPropagate;

import java.lang.annotation.*;

@AopAnnotation
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Fallback {

    String method();

    @Documented
    @AopPropagate
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    @interface Reason {}
}
