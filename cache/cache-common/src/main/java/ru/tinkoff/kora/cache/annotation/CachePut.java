package ru.tinkoff.kora.cache.annotation;

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
@Retention(RetentionPolicy.RUNTIME)
@AopAnnotation
public @interface CachePut {

    /**
     * @return cache name (correlate with name in configuration file)
     */
    String name();

    /**
     * @return {@link ru.tinkoff.kora.cache.CacheManager} implementation associated with cache (will use impl from default module if any present in Graph)
     */
    Class<?>[] tags() default {};

    /**
     * Limit the automatic {@link CacheKeyMapper} to the given parameter names. Mutually exclusive with
     *
     * @return The parameter names that make up the key.
     */
    String[] parameters() default {};
}
