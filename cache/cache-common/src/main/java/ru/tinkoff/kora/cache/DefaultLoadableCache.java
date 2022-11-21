package ru.tinkoff.kora.cache;

import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class DefaultLoadableCache<K, V> implements LoadableCache<K, V> {
    private final Cache<K, V> cache;
    private final CacheLoader<K, V> cacheLoader;


    public DefaultLoadableCache(Cache<K, V> cache, CacheLoader<K, V> cacheLoader) {
        this.cache = cache;
        this.cacheLoader = cacheLoader;
    }

    @Nullable
    @Override
    public V get(@Nonnull K key) {
        var fromCache = cache.get(key);
        if (fromCache != null) {
            return fromCache;
        }

        var value = cacheLoader.load(key);
        if (value != null) {
            cache.put(key, value);
        }

        return value;
    }

    @Nonnull
    @Override
    public Mono<V> getAsync(@Nonnull K key) {
        return cache.getAsync(key)
            .switchIfEmpty(
                cacheLoader.loadAsync(key)
                    .flatMap(value -> cache.putAsync(key, value).thenReturn(value))
            );
    }
}
