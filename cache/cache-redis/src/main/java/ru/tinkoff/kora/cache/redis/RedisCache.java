package ru.tinkoff.kora.cache.redis;

import ru.tinkoff.kora.cache.AsyncCache;

/**
 * This module is no longer maintained, it was replaced with new one.
 * <p>
 * Use dependency - ru.tinkoff.kora:cache-redis-lettuce AND RedisCache
 * <p>
 * Check documentation for more information
 */
@Deprecated
public interface RedisCache<K, V> extends AsyncCache<K, V> {

}
