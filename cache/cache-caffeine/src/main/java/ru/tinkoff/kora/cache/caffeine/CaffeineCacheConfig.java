package ru.tinkoff.kora.cache.caffeine;


import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Map;

@ConfigValueExtractor
public interface CaffeineCacheConfig {
    String DEFAULT = "default";
    NamedCacheConfig DEFAULT_CONFIG = new NamedCacheConfig(null, null, 100_000L, null);

    default Map<String, NamedCacheConfig> caffeine() {
        return Map.of();
    }

    @Nonnull
    default NamedCacheConfig getByName(@Nonnull String name) {
        final NamedCacheConfig defaultConfig = this.caffeine().getOrDefault(DEFAULT, DEFAULT_CONFIG);
        final NamedCacheConfig namedConfig = this.caffeine().get(name);
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

    @ConfigValueExtractor
    record NamedCacheConfig(
        @Nullable Duration expireAfterWrite,
        @Nullable Duration expireAfterAccess,
        @Nullable Long maximumSize,
        @Nullable Integer initialSize) {

    }
}
