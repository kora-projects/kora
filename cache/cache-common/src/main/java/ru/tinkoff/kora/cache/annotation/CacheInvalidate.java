package ru.tinkoff.kora.cache.annotation;

import ru.tinkoff.kora.cache.CacheKeyMapper;
import ru.tinkoff.kora.common.AopAnnotation;

import java.lang.annotation.*;

/**
 * An annotation that can be applied at the type or method level to indicate that the annotated operation should
 * cause the eviction of the given caches.
 */
@Repeatable(CacheInvalidates.class)
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@AopAnnotation
public @interface CacheInvalidate {

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

    /**
     * @return Whether all values within the cache should be evicted or only those for the generated key
     */
    boolean invalidateAll() default false;
}
