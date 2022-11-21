package ru.tinkoff.kora.cache.redis;


import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Map;

public record RedisCacheConfig(@Nullable Map<String, NamedCacheConfig> redis) {

    private static final String DEFAULT = "default";

    private static final NamedCacheConfig DEFAULT_CONFIG = new NamedCacheConfig(null, null);

    public NamedCacheConfig getByName(@Nonnull String name) {
        if (redis == null) {
            return DEFAULT_CONFIG;
        }

        final NamedCacheConfig defaultConfig = redis.getOrDefault(DEFAULT, DEFAULT_CONFIG);
        final NamedCacheConfig namedConfig = redis.get(name);
        if (namedConfig == null) {
            return defaultConfig;
        }

        return new NamedCacheConfig(
            namedConfig.expireAfterWrite == null ? defaultConfig.expireAfterWrite : namedConfig.expireAfterWrite,
            namedConfig.expireAfterAccess == null ? defaultConfig.expireAfterAccess : namedConfig.expireAfterAccess
        );
    }

    public record NamedCacheConfig(@Nullable Duration expireAfterWrite,
                                   @Nullable Duration expireAfterAccess) {}
}
