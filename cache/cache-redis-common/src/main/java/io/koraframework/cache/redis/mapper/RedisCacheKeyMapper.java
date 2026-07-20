package io.koraframework.cache.redis.mapper;

import io.koraframework.cache.CacheKeyMapper;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.function.Function;

/**
 * Contract for converting method arguments {@link CacheKeyMapper} into the final key that will be used in Cache implementation.
 */
public interface RedisCacheKeyMapper<K> extends Function<K, byte[]> {

    /**
     * Is used to delimiter composite key such as {@link CacheKeyMapper}
     */
    byte[] DELIMITER = ":".getBytes(StandardCharsets.UTF_8);

    @Override
    byte[] apply(@Nullable K key);
}
