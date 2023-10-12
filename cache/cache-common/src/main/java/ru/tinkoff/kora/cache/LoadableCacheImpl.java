package ru.tinkoff.kora.cache;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

final class LoadableCacheImpl<K, V> implements LoadableCache<K, V> {

    private final Cache<K, V> cache;
    private final Function<Collection<K>, Map<K, V>> cacheLoader;

    LoadableCacheImpl(Cache<K, V> cache, Function<Collection<K>, Map<K, V>> cacheLoader) {
        this.cache = cache;
        this.cacheLoader = cacheLoader;
    }

    @Nullable
    @Override
    public V get(@Nonnull K key) {
        return cache.computeIfAbsent(key, k -> cacheLoader.apply(Set.of(k)).values().stream().findFirst().orElse(null));
    }

    @Nonnull
    @Override
    public Map<K, V> get(@Nonnull Collection<K> keys) {
        return cache.computeIfAbsent(keys, cacheLoader::apply);
    }
}
