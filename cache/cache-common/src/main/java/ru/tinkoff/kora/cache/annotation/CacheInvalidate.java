package ru.tinkoff.kora.cache.annotation;

import ru.tinkoff.kora.cache.Cache;
import ru.tinkoff.kora.common.AopAnnotation;

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
     * @return cache name (correlate with name in configuration file)
     */
    Class<? extends Cache<?, ?>> value();

    /**
     * @return The parameter names that make up the key.
     */
    String[] parameters() default {};

    /**
     * @return Whether all values within the cache should be evicted or only those for the generated key
     */
    boolean invalidateAll() default false;
}
