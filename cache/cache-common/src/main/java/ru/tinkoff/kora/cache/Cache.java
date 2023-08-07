package ru.tinkoff.kora.cache;

import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Represents base Cache contract.
 */
public interface Cache<K, V> {

    @Nonnull
    default LoadableCache<K, V> asLoadable(@Nonnull CacheLoader<K, V> cacheLoader) {
        return new LoadableCacheImpl<>(this, cacheLoader);
    }

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

    /**
     * Cache the specified value using the specified key.
     *
     * @param key   the key with which the specified value is to be associated
     * @param value the value to be associated with the specified key
     */
    @Nonnull
    V put(@Nonnull K key, @Nonnull V value);

    /**
     * @param key             to look for value or compute and put if absent
     * @param mappingFunction to use for value computing
     * @return existing or computed value
     */
    V computeIfAbsent(@Nonnull K key, @Nonnull Function<K, V> mappingFunction);

    /**
     * @param keys            to look for value or compute and put if absent
     * @param mappingFunction to use for value computing
     * @return existing or computed value
     */
    @Nonnull
    Map<K, V> computeIfAbsent(@Nonnull Collection<K> keys, @Nonnull Function<Set<K>, Map<K, V>> mappingFunction);

    /**
     * Invalidate the value for the given key.
     *
     * @param key The key to invalid
     */
    void invalidate(@Nonnull K key);

    void invalidate(@Nonnull Collection<K> keys);

    /**
     * Invalidate all cached values within this cache.
     */
    void invalidateAll();

    /**
     * Cache the specified value using the specified key.
     *
     * @param key   the key with which the specified value is to be associated
     * @param value the value to be associated with the specified key
     * @return Void
     */
    @Nonnull
    Mono<V> putAsync(@Nonnull K key, @Nonnull V value);


    /**
     * @param key             to look for value or compute and put if absent
     * @param mappingFunction to use for value computing
     * @return existing or computed value
     */
    Mono<V> computeIfAbsentAsync(@Nonnull K key, @Nonnull Function<K, Mono<V>> mappingFunction);

    /**
     * @param keys            to look for value or compute and put if absent
     * @param mappingFunction to use for value computing
     * @return existing or computed value
     */
    @Nonnull
    Mono<Map<K, V>> computeIfAbsentAsync(@Nonnull Collection<K> keys, @Nonnull Function<Set<K>, Mono<Map<K, V>>> mappingFunction);

    /**
     * Invalidate the value for the given key.
     *
     * @param key The key to invalid
     */
    @Nonnull
    Mono<Boolean> invalidateAsync(@Nonnull K key);

    Mono<Boolean> invalidateAsync(@Nonnull Collection<K> keys);

    /**
     * Invalidate all cached values within this cache.
     */
    @Nonnull
    Mono<Boolean> invalidateAllAsync();
}
