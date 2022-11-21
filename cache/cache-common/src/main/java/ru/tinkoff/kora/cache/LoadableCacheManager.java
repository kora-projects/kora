package ru.tinkoff.kora.cache;

import javax.annotation.Nonnull;

/**
 * {@link LoadableCache} manager contract that is responsible for retrieval Cache implementations.
 */
public interface LoadableCacheManager<K, V> {

    /**
     * @param name cache name
     * @return identified {@link LoadableCache} if it exists or creates new one
     */
    LoadableCache<K, V> getLoadableCache(@Nonnull String name);
}
