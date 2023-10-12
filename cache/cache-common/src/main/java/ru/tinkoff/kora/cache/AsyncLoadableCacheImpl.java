package ru.tinkoff.kora.cache;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

final class AsyncLoadableCacheImpl<K, V> implements AsyncLoadableCache<K, V> {

    private final AsyncCache<K, V> cache;
    private final Function<Collection<K>, CompletionStage<Map<K, V>>> cacheLoader;

    AsyncLoadableCacheImpl(AsyncCache<K, V> cache, Function<Collection<K>, CompletionStage<Map<K, V>>> cacheLoader) {
        this.cache = cache;
        this.cacheLoader = cacheLoader;
    }

    @Nullable
    @Override
    public V get(@Nonnull K key) {
        return getAsync(key).toCompletableFuture().join();
    }

    @Nonnull
    @Override
    public Map<K, V> get(@Nonnull Collection<K> keys) {
        return getAsync(keys).toCompletableFuture().join();
    }

    @Nonnull
    @Override
    public CompletionStage<V> getAsync(@Nonnull K key) {
        return cache.computeIfAbsentAsync(key, k -> cacheLoader.apply(Set.of(k))
            .thenApply(r -> r.values().stream().findFirst().orElse(null)));
    }

    @Nonnull
    @Override
    public CompletionStage<Map<K, V>> getAsync(@Nonnull Collection<K> keys) {
        return cache.computeIfAbsentAsync(keys, cacheLoader::apply);
    }
}
