package io.koraframework.cache.annotation;

import io.koraframework.common.AopAnnotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @see CachePut
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
@AopAnnotation
public @interface CachePuts {

    CachePut[] value();
}
