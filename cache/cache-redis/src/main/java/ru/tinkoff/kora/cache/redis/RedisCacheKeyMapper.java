package ru.tinkoff.kora.cache.redis;

import ru.tinkoff.kora.cache.CacheKeyMapper;

import java.nio.charset.StandardCharsets;
import java.util.function.Function;

/**
 * Contract for converting method arguments {@link CacheKeyMapper} into the final key that will be used in Cache implementation.
 * Use dependency - ru.tinkoff.kora:cache-redis-lettuce
 */
@Deprecated
public interface RedisCacheKeyMapper<K> extends Function<K, byte[]> {

    /**
     * Is used to delimiter composite key such as {@link CacheKeyMapper}
     */
    byte[] DELIMITER = ":".getBytes(StandardCharsets.UTF_8);
}
