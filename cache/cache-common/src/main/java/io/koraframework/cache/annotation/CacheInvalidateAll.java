package io.koraframework.cache.annotation;

import io.koraframework.cache.Cache;
import io.koraframework.common.AopAnnotation;

import java.lang.annotation.*;

/**
 * An annotation that can be applied at the type or method level to indicate that the annotated operation should
 * cause the eviction of all data from the given caches.
 */
@Repeatable(CacheInvalidateAlls.class)
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
@AopAnnotation
public @interface CacheInvalidateAll {

    /**
     * @return cache implementation
     */
    Class<? extends Cache<?, ?>> value();
}
