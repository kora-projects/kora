package ru.tinkoff.kora.cache;

import jakarta.annotation.Nonnull;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * Analog of Caffeine LoadableCache
 */
public interface AsyncLoadableCache<K, V> extends LoadableCache<K, V> {

    /**
     * Resolve the given value for the given key.
     *
     * @param key The cache key
     * @return value associated with the key or null if no value is specified
     */
    @Nonnull
    CompletionStage<V> getAsync(@Nonnull K key);

    @Nonnull
    CompletionStage<Map<K, V>> getAsync(@Nonnull Collection<K> keys);
}
