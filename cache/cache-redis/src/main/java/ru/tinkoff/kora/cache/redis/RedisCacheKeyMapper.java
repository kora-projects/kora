package ru.tinkoff.kora.cache.redis;

import ru.tinkoff.kora.cache.CacheKeyMapper;

import java.nio.charset.StandardCharsets;
import java.util.function.Function;

/**
 * This module is no longer maintained, it was replaced with new one.
 * <p>
 * Use dependency - ru.tinkoff.kora:cache-redis-lettuce AND RedisCacheKeyMapper
 * <p>
 * Check documentation for more information
 * <p>
 * Contract for converting method arguments {@link CacheKeyMapper} into the final key that will be used in Cache implementation.
 */
@Deprecated
public interface RedisCacheKeyMapper<K> extends Function<K, byte[]> {

    /**
     * Is used to delimiter composite key such as {@link CacheKeyMapper}
     */
    byte[] DELIMITER = ":".getBytes(StandardCharsets.UTF_8);
}
