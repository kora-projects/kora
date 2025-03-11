package ru.tinkoff.kora.cache.redis;

/**
 * This module is no longer maintained, it was replaced with new one.
 * <p>
 * Use dependency - ru.tinkoff.kora:cache-redis-lettuce AND RedisCacheValueMapper
 * <p>
 * Check documentation for more information
 * <p>
 * Converts cache value into serializer value to store in cache.
 */
@Deprecated
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
    V read(byte[] serializedValue);
}
