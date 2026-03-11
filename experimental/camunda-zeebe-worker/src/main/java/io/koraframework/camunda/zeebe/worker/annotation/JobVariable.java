package io.koraframework.camunda.zeebe.worker.annotation;

import io.koraframework.common.AopAnnotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@AopAnnotation
public @interface JobVariable {

    String value() default "";
}
