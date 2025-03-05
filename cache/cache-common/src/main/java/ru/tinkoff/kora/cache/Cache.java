package ru.tinkoff.kora.cache;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Represents Synchronous Cache contract.
 */
public interface Cache<K, V> {

    @Nonnull
    default LoadableCache<K, V> asLoadableSimple(@Nonnull Function<K, V> cacheLoader) {
        return new LoadableCacheImpl<>(this, (keys) -> {
            final Map<K, V> result = new HashMap<>();

            for (K key : keys) {
                var loaded = cacheLoader.apply(key);
                result.put(key, loaded);
            }

            return result;
        });
    }

    @Nonnull
    default LoadableCache<K, V> asLoadable(@Nonnull Function<Collection<K>, Map<K, V>> cacheLoader) {
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
     * Cache the specified value using the specified key.
     *
     * @param key   the key with which the specified value is to be associated
     * @param value the value to be associated with the specified key
     */
    @Nonnull
    V put(@Nonnull K key, @Nonnull V value);

    /**
     * Cache the specified value using the specified key.
     *
     * @param keyAndValues the keys and values with which the specified value is to be associated
     */
    @Nonnull
    Map<K, V> put(@Nonnull Map<K, V> keyAndValues);

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

    @Nonnull
    static <K, V> Builder<K, V> builder(@Nonnull Cache<K, V> cache) {
        return new FacadeCacheBuilder<>(cache);
    }

    interface Builder<K, V> {

        @Nonnull
        Builder<K, V> addCache(@Nonnull Cache<K, V> cache);

        @Nonnull
        Cache<K, V> build();
    }
}
