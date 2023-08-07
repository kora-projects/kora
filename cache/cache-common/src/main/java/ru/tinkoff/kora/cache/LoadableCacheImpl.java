package ru.tinkoff.kora.cache;

import reactor.core.publisher.Mono;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Map;

final class LoadableCacheImpl<K, V> implements LoadableCache<K, V> {

    private final Cache<K, V> cache;
    private final CacheLoader<K, V> cacheLoader;

    LoadableCacheImpl(Cache<K, V> cache, CacheLoader<K, V> cacheLoader) {
        this.cache = cache;
        this.cacheLoader = cacheLoader;
    }

    @Nullable
    @Override
    public V get(@Nonnull K key) {
        return cache.computeIfAbsent(key, cacheLoader::load);
    }

    @Nonnull
    @Override
    public Map<K, V> get(@Nonnull Collection<K> keys) {
        return cache.computeIfAbsent(keys, cacheLoader::load);
    }

    @Nonnull
    @Override
    public Mono<V> getAsync(@Nonnull K key) {
        return cache.computeIfAbsentAsync(key, cacheLoader::loadAsync);
    }

    @Nonnull
    @Override
    public Mono<Map<K, V>> getAsync(@Nonnull Collection<K> keys) {
        return cache.computeIfAbsentAsync(keys, cacheLoader::loadAsync);
    }
}
