package ru.tinkoff.kora.cache.annotation;

import ru.tinkoff.kora.cache.Cache;
import ru.tinkoff.kora.cache.CacheKeyMapper;
import ru.tinkoff.kora.common.AopAnnotation;

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
     * @return cache name (correlate with name in configuration file)
     */
    Class<? extends Cache<?, ?>> value();

    /**
     * Limit the automatic {@link CacheKeyMapper} to the given parameter names. Mutually exclusive with
     *
     * @return The parameter names that make up the key.
     */
    String[] parameters() default {};
}
