package ru.tinkoff.kora.cache.redis;

/**
 * Converts cache value into serializer value to store in cache.
 * Use dependency - ru.tinkoff.kora:cache-redis-lettuce
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
