package ru.tinkoff.kora.cache.annotation;

import ru.tinkoff.kora.cache.Cache;
import ru.tinkoff.kora.cache.CacheKeyMapper;
import ru.tinkoff.kora.common.AopAnnotation;

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
