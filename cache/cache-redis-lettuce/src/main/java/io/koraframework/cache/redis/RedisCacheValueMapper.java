package io.koraframework.cache.redis;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * Converts cache value into serializer value to store in cache.
 */
public interface RedisCacheValueMapper<V> {

    /**
     * @param value to serialize
     * @return value serialized
     */
    @Nonnull
    byte[] write(@Nonnull V value);

    /**
     * @param serializedValue to deserialize
     * @return value deserialized
     */
    @Nullable
    V read(@Nullable byte[] serializedValue);
}
