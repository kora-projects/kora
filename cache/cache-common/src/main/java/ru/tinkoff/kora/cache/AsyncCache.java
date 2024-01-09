package ru.tinkoff.kora.cache;

import jakarta.annotation.Nonnull;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Represents Async Cache contract.
 */
public interface AsyncCache<K, V> extends Cache<K, V> {

    @Nonnull
    default AsyncLoadableCache<K, V> asLoadableAsyncSimple(@Nonnull Function<K, CompletionStage<V>> cacheLoader) {
        final Function<Collection<K>, CompletionStage<Map<K, V>>> func = (keys) -> {
            final Map<K, CompletableFuture<V>> collected = new HashMap<>();

            for (K key : keys) {
                var loaded = cacheLoader.apply(key).toCompletableFuture();
                collected.put(key, loaded);
            }

            return CompletableFuture.allOf(collected.values().toArray(CompletableFuture[]::new))
                .thenApply(r -> {
                    final Map<K, V> result = new HashMap<>();
                    collected.forEach((k, v) -> result.put(k, v.join()));
                    return result;
                });
        };

        return new AsyncLoadableCacheImpl<>(this, func);
    }

    @Nonnull
    default AsyncLoadableCache<K, V> asLoadableAsync(@Nonnull Function<Collection<K>, CompletionStage<Map<K, V>>> cacheLoader) {
        return new AsyncLoadableCacheImpl<>(this, cacheLoader);
    }

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

    /**
     * Cache the specified value using the specified key.
     *
     * @param key   the key with which the specified value is to be associated
     * @param value the value to be associated with the specified key
     * @return Void
     */
    @Nonnull
    CompletionStage<V> putAsync(@Nonnull K key, @Nonnull V value);

    /**
     * Cache the specified value using the specified key.
     *
     * @param keyAndValues the keys and values with which the specified value is to be associated
     */
    @Nonnull
    CompletionStage<Map<K, V>> putAsync(@Nonnull Map<K, V> keyAndValues);

    /**
     * @param key             to look for value or compute and put if absent
     * @param mappingFunction to use for value computing
     * @return existing or computed value
     */
    @Nonnull
    CompletionStage<V> computeIfAbsentAsync(@Nonnull K key, @Nonnull Function<K, CompletionStage<V>> mappingFunction);

    /**
     * @param keys            to look for value or compute and put if absent
     * @param mappingFunction to use for value computing
     * @return existing or computed value
     */
    @Nonnull
    CompletionStage<Map<K, V>> computeIfAbsentAsync(@Nonnull Collection<K> keys, @Nonnull Function<Set<K>, CompletionStage<Map<K, V>>> mappingFunction);

    /**
     * Invalidate the value for the given key.
     *
     * @param key The key to invalid
     */
    @Nonnull
    CompletionStage<Boolean> invalidateAsync(@Nonnull K key);

    CompletionStage<Boolean> invalidateAsync(@Nonnull Collection<K> keys);

    /**
     * Invalidate all cached values within this cache.
     */
    @Nonnull
    CompletionStage<Boolean> invalidateAllAsync();

    @Nonnull
    static <K, V> AsyncCache.Builder<K, V> builder(@Nonnull AsyncCache<K, V> cache) {
        return new AsyncFacadeCacheBuilder<>(cache);
    }

    interface Builder<K, V> {

        @Nonnull
        AsyncCache.Builder<K, V> addCache(@Nonnull AsyncCache<K, V> cache);

        @Nonnull
        AsyncCache<K, V> build();
    }
}
