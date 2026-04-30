package io.koraframework.cache.annotation;

import io.koraframework.cache.Cache;
import io.koraframework.common.AopAnnotation;

import java.lang.annotation.*;

/**
 * An annotation that can be applied at the type or method level to indicate that the annotated operation should
 * cause the eviction of the given caches.
 */
@Repeatable(CacheInvalidates.class)
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
@AopAnnotation
public @interface CacheInvalidate {

    /**
     * @return cache implementation
     */
    Class<? extends Cache<?, ?>> value();

    /**
     * @return The parameter names that make up the key.
     */
    String[] args() default {};
}
