package io.koraframework.cache.annotation;

import io.koraframework.cache.Cache;
import io.koraframework.common.AopAnnotation;

import java.lang.annotation.*;

/**
 * An annotation that can be applied at the type or method level to indicate that the return value of the method
 * should be cached for the configured {@link Cacheable#value()}.
 */
@Repeatable(Cacheables.class)
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
@AopAnnotation
public @interface Cacheable {

    /**
     * @return cache implementation
     */
    Class<? extends Cache<?, ?>> value();

    /**
     * @return The parameter names that make up the key.
     */
    String[] args() default {};
}
