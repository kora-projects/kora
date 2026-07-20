package io.koraframework.cache.annotation;

import io.koraframework.cache.Cache;
import io.koraframework.common.annotation.AopAnnotation;

import java.lang.annotation.*;

/**
 * An annotation that can be applied at the type or method level to indicate that the annotated operation should
 * cause the return value to be cached within the given cache name. Unlike {@link Cacheable} this annotation will never
 * skip the original invocation.
 */
@Repeatable(CachePuts.class)
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
@AopAnnotation
public @interface CachePut {

    /**
     * @return cache implementation
     */
    Class<? extends Cache<?, ?>> value();

    /**
     * @return The parameter names that make up the key.
     */
    String[] args() default {};

    /**
     * @return cache update execution mode
     */
    CacheMode mode() default CacheMode.SYNC;
}
