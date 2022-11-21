package ru.tinkoff.kora.cache.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.stats.StatsCounter;

import javax.annotation.Nonnull;

public final class CaffeineCacheFactory {

    @Nonnull
    public <K, V> Cache<K, V> build(@Nonnull CaffeineCacheConfig.NamedCacheConfig config) {
        final Caffeine<K, V> builder = (Caffeine<K, V>) Caffeine.newBuilder();
        if (config.expireAfterWrite() != null)
            builder.expireAfterWrite(config.expireAfterWrite());
        if (config.expireAfterAccess() != null)
            builder.expireAfterAccess(config.expireAfterAccess());
        if (config.initialSize() != null)
            builder.initialCapacity(config.initialSize());
        if (config.maximumSize() != null)
            builder.maximumSize(config.maximumSize());
        return builder.recordStats(StatsCounter::disabledStatsCounter).build();
    }
}
