package io.koraframework.cache.redis;

import io.koraframework.cache.Cache;

import java.time.Duration;
import java.util.Map;

public interface RedisCache<K, V> extends Cache<K, V> {

    V putExpireAfterWrite(K key, V value, Duration expireAfterWrite);

    Map<K, V> putExpireAfterWrite(Map<K, V> keyAndValues, Duration expireAfterWrite);
}
