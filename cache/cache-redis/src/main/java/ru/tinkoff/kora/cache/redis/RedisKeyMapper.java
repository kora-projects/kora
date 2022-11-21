package ru.tinkoff.kora.cache.redis;

import ru.tinkoff.kora.cache.CacheKey;

import java.util.function.Function;

/**
 * Contract for converting method arguments {@link CacheKey} into the final key that will be used in Cache implementation.
 */
public interface RedisKeyMapper<K> extends Function<K, byte[]> {

}
