package ru.tinkoff.kora.cache;

import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Represents Synchronous Cache contract.
 */
public interface Cache<K, V> {

    default LoadableCache<K, V> asLoadableSimple(Function<K, V> cacheLoader) {
        return new LoadableCacheImpl<>(this, (keys) -> {
            final Map<K, V> result = new HashMap<>();

            for (K key : keys) {
                var loaded = cacheLoader.apply(key);
                result.put(key, loaded);
            }

            return result;
        });
    }

    default LoadableCache<K, V> asLoadable(Function<Collection<K>, Map<K, V>> cacheLoader) {
        return new LoadableCacheImpl<>(this, cacheLoader);
    }

    /**
     * Resolve the given value for the given key.
     *
     * @param key The cache key
     * @return value associated with the key
     */
    @Nullable
    V get(K key);

    Map<K, V> get(Collection<K> keys);

    /**
     * Cache the specified value using the specified key.
     *
     * @param key   the key with which the specified value is to be associated
     * @param value the value to be associated with the specified key
     */
    V put(K key, V value);

    /**
     * Cache the specified value using the specified key.
     *
     * @param keyAndValues the keys and values with which the specified value is to be associated
     */
    Map<K, V> put(Map<K, V> keyAndValues);

    /**
     * @param key             to look for value or compute and put if absent
     * @param mappingFunction to use for value computing
     * @return existing or computed value
     */
    V computeIfAbsent(K key, Function<K, V> mappingFunction);

    /**
     * @param keys            to look for value or compute and put if absent
     * @param mappingFunction to use for value computing
     * @return existing or computed value
     */
    Map<K, V> computeIfAbsent(Collection<K> keys, Function<Set<K>, Map<K, V>> mappingFunction);

    /**
     * Invalidate the value for the given key.
     *
     * @param key The key to invalid
     */
    void invalidate(K key);

    void invalidate(Collection<K> keys);

    /**
     * Invalidate all cached values within this cache.
     */
    void invalidateAll();

    static <K, V> Builder<K, V> builder(Cache<K, V> cache) {
        return new FacadeCacheBuilder<>(cache);
    }

    interface Builder<K, V> {

        Builder<K, V> addCache(Cache<K, V> cache);

        Cache<K, V> build();
    }
}
