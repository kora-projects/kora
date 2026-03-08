package io.koraframework.cache.annotation;

import io.koraframework.common.AopAnnotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @see Cacheable
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
@AopAnnotation
public @interface Cacheables {

    Cacheable[] value();
}
