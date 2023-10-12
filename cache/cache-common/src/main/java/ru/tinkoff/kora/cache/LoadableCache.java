package ru.tinkoff.kora.cache;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

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
    V get(@Nonnull K key);

    @Nonnull
    Map<K, V> get(@Nonnull Collection<K> keys);
}
