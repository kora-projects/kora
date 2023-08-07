package ru.tinkoff.kora.cache;

import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;

/**
 * Analog of Caffeine LoadableCache, require {@link CacheLoader} associated
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

    /**
     * Resolve the given value for the given key.
     *
     * @param key The cache key
     * @return value associated with the key or {@link Mono#empty()} if no value is specified
     */
    @Nonnull
    Mono<V> getAsync(@Nonnull K key);

    @Nonnull
    Mono<Map<K, V>> getAsync(@Nonnull Collection<K> keys);
}
