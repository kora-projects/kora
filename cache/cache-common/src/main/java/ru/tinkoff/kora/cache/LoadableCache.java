package ru.tinkoff.kora.cache;

import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Map;

/**
 * Analog of Caffeine LoadableCache
 */
public interface LoadableCache<K, V> {

    /**
     * Resolve the given value for the given key.
     *
     * @param key The cache key
     * @return value associated with the key
     */
    @Nullable
    V get(K key);

    Map<K, V> get(Collection<K> keys);
}
