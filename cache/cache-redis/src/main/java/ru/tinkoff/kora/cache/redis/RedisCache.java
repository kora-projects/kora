package ru.tinkoff.kora.cache.redis;

import ru.tinkoff.kora.cache.AsyncCache;

/**
 * Use dependency - ru.tinkoff.kora:cache-redis-lettuce
 */
@Deprecated
public interface RedisCache<K, V> extends AsyncCache<K, V> {

}
