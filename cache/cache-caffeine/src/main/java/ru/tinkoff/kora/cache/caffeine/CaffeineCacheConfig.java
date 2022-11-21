package ru.tinkoff.kora.cache.caffeine;


import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Map;

public record CaffeineCacheConfig(@Nullable Map<String, NamedCacheConfig> caffeine) {

    private static final String DEFAULT = "default";

    private static final NamedCacheConfig DEFAULT_CONFIG = new NamedCacheConfig(null, null, 100_000L, null);

    @Nonnull
    public NamedCacheConfig getByName(@Nonnull String name) {
        if (caffeine == null) {
            return DEFAULT_CONFIG;
        }

        final NamedCacheConfig defaultConfig = caffeine.getOrDefault(DEFAULT, DEFAULT_CONFIG);
        final NamedCacheConfig namedConfig = caffeine.get(name);
        if (namedConfig == null) {
            return defaultConfig;
        }

        return new NamedCacheConfig(
            namedConfig.expireAfterWrite == null ? defaultConfig.expireAfterWrite : namedConfig.expireAfterWrite,
            namedConfig.expireAfterAccess == null ? defaultConfig.expireAfterAccess : namedConfig.expireAfterAccess,
            namedConfig.maximumSize == null ? defaultConfig.maximumSize : namedConfig.maximumSize,
            namedConfig.initialSize == null ? defaultConfig.initialSize : namedConfig.initialSize
        );
    }

    public record NamedCacheConfig(@Nullable Duration expireAfterWrite,
                                   @Nullable Duration expireAfterAccess,
                                   @Nullable Long maximumSize,
                                   @Nullable Integer initialSize) {

    }
}
