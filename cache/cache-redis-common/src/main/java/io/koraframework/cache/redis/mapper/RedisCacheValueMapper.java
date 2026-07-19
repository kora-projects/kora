package io.koraframework.cache.redis.mapper;

import org.jspecify.annotations.Nullable;

/**
 * Converts cache value into serializer value to store in cache.
 */
public interface RedisCacheValueMapper<V> {

    /**
     * @param value to serialize
     * @return value serialized
     */
    byte[] write(V value);

    /**
     * @param serializedValue to deserialize
     * @return value deserialized
     */
    @Nullable
    V read(byte @Nullable [] serializedValue);
}
