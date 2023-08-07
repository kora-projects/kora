package ru.tinkoff.kora.cache;

import javax.annotation.Nonnull;

public interface CacheBuilder<K, V> {

    @Nonnull
    CacheBuilder<K, V> addCache(@Nonnull Cache<K, V> cache);

    @Nonnull
    Cache<K, V> build();

    @Nonnull
    static <K, V> CacheBuilder<K, V> builder(@Nonnull Cache<K, V> cache) {
        return new FacadeCacheBuilder<>(cache);
    }
}
