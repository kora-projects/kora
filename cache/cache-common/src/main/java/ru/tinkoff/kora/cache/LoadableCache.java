package ru.tinkoff.kora.cache;

import reactor.core.publisher.Mono;

import javax.annotation.Nullable;
import javax.annotation.Nonnull;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

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

    /**
     * Resolve the given value for the given key.
     *
     * @param key The cache key
     * @return value associated with the key or {@link Mono#empty()} if no value is specified
     */
    @Nonnull
    Mono<V> getAsync(@Nonnull K key);

    /**
     * Create default loadable cache implementation
     * @param cache Cache to store loaded value
     * @param cacheLoader Cache loader
     * @return default loadable cache instance
     * @param <K> Cache key
     * @param <V> Cache value
     */
    @Nonnull
    static <K, V> LoadableCache<K, V> create(Cache<K, V> cache, CacheLoader<K, V> cacheLoader) {
        return new DefaultLoadableCache<>(cache, cacheLoader);
    }

    /**
     * Create default loadable cache implementation for blocking operation
     * @param cache Cache to store loaded value
     * @param executor Executor to submit load task on async call
     * @param loader Blocking load operation
     * @return default loadable cache instance
     * @param <K> Cache key
     * @param <V> Cache value
     */
    @Nonnull
    static <K, V> LoadableCache<K, V> blocking(Cache<K, V> cache, ExecutorService executor, Function<K, V> loader) {
        return create(cache, new CacheLoader.BlockingCacheLoader<>(executor, loader));
    }

    /**
     * Create default loadable cache implementation for non-blocking operation
     * @param cache Cache to store loaded value
     * @param loader Some long computation
     * @return default loadable cache instance
     * @param <K> Cache key
     * @param <V> Cache value
     */
    @Nonnull
    static <K, V> LoadableCache<K, V> nonBlocking(Cache<K, V> cache, Function<K, V> loader) {
        return create(cache, new CacheLoader.NonBlockingCacheLoader<>(loader));
    }

    /**
     * Create default loadable cache implementation for async operation
     * @param cache Cache to store loaded value
     * @param loader Async
     * @return default loadable cache instance
     * @param <K> Cache key
     * @param <V> Cache value
     */
    @Nonnull
    static <K, V> LoadableCache<K, V> async(Cache<K, V> cache, Function<K, Mono<V>> loader) {
        return create(cache, new CacheLoader.AsyncCacheLoader<>(loader));
    }
}
