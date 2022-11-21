package ru.tinkoff.kora.cache;

import javax.annotation.Nonnull;
import java.util.function.Function;

/**
 * Cache manager contract that is responsible for retrieval Cache implementations.
 */
public interface CacheManager<K, V> {

    /**
     * @param name cache name
     * @return identified {@link Cache} if it exists or creates new one
     */
    Cache<K, V> getCache(@Nonnull String name);

    interface Builder<K, V> {

        @Nonnull
        Builder<K, V> addFacadeManager(@Nonnull CacheManager<K, V> cacheManager);

        @Nonnull
        Builder<K, V> addFacadeFunction(@Nonnull Function<String, Cache<K, V>> cacheFunction);

        @Nonnull
        CacheManager<K, V> build();
    }

    @Nonnull
    static <K, V> Builder<K, V> builder() {
        return new FacadeCacheManagerBuilder<>();
    }
}
