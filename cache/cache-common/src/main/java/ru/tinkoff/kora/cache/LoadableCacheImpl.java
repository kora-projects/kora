package ru.tinkoff.kora.cache;

import org.jspecify.annotations.Nullable;

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
    public V get(K key) {
        return cache.computeIfAbsent(key, k -> {
            final Map<K, V> result = cacheLoader.apply(Set.of(k));
            if (result.isEmpty()) {
                return null;
            } else {
                return result.values().iterator().next();
            }
        });
    }

    @Override
    public Map<K, V> get(Collection<K> keys) {
        return cache.computeIfAbsent(keys, cacheLoader::apply);
    }
}
