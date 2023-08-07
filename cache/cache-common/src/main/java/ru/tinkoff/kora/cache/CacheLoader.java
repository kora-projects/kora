package ru.tinkoff.kora.cache;

import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

public interface CacheLoader<K, V> {

    /**
     * Computes or retrieves the value corresponding to {@code key}.
     *
     * @param key to look value for
     * @return value associated with key
     */
    @Nullable
    V load(@Nonnull K key);

    /**
     * Computes or retrieves the value corresponding to {@code key}.
     *
     * @param keys to look value for
     * @return value associated with key
     */
    @Nullable
    Map<K, V> load(@Nonnull Collection<K> keys);

    /**
     * Computes or retrieves the value corresponding to {@code key} asynchronously.
     *
     * @param key to look value for
     * @return value associated with key or {@link Mono#empty()}
     */
    @Nonnull
    Mono<V> loadAsync(@Nonnull K key);

    /**
     * Computes or retrieves the value corresponding to {@code key} asynchronously.
     *
     * @param keys to look value for
     * @return value associated with key or {@link Mono#empty()}
     */
    Mono<Map<K, V>> loadAsync(@Nonnull Collection<K> keys);

    @Nonnull
    static <K, V> CacheLoader<K, V> blocking(@Nonnull Function<K, V> loader,
                                             @Nonnull ExecutorService executor) {
        return new CacheLoaderImpls.BlockingCacheLoader<>(executor, loader);
    }

    /**
     * Create default loadable cache implementation for blocking operation
     *
     * @param executor   Executor to submit load task on async call
     * @param loader     Blocking load operation
     * @param loaderMany Blocking load for multiple keys
     * @param <K>        Cache key
     * @param <V>        Cache value
     * @return default loadable cache instance
     */
    @Nonnull
    static <K, V> CacheLoader<K, V> blocking(@Nonnull Function<K, V> loader,
                                             @Nonnull Function<Collection<K>, Map<K, V>> loaderMany,
                                             @Nonnull ExecutorService executor) {
        return new CacheLoaderImpls.BlockingCacheLoader<>(executor, loader, loaderMany);
    }

    @Nonnull
    static <K, V> CacheLoader<K, V> nonBlocking(@Nonnull Function<K, V> loader) {
        return new CacheLoaderImpls.NonBlockingCacheLoader<>(loader);
    }

    /**
     * Create default loadable cache implementation for non-blocking operation
     *
     * @param loader Some long computation
     * @param <K>    Cache key
     * @param <V>    Cache value
     * @return default loadable cache instance
     */
    @Nonnull
    static <K, V> CacheLoader<K, V> nonBlocking(@Nonnull Function<K, V> loader,
                                                @Nonnull Function<Collection<K>, Map<K, V>> loaderMany) {
        return new CacheLoaderImpls.NonBlockingCacheLoader<>(loader, loaderMany);
    }

    /**
     * Create default loadable cache implementation for async operation
     *
     * @param loader Async
     * @param <K>    Cache key
     * @param <V>    Cache value
     * @return default loadable cache instance
     */
    @Nonnull
    static <K, V> CacheLoader<K, V> async(@Nonnull Function<K, Mono<V>> loader) {
        return new CacheLoaderImpls.AsyncCacheLoader<>(loader);
    }

    @Nonnull
    static <K, V> CacheLoader<K, V> async(@Nonnull Function<K, Mono<V>> loader,
                                          @Nonnull Function<Collection<K>, Mono<Map<K, V>>> loaderMany) {
        return new CacheLoaderImpls.AsyncCacheLoader<>(loader, loaderMany);
    }
}
