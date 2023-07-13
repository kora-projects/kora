package ru.tinkoff.kora.cache.redis;


import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Map;

@ConfigValueExtractor
public interface RedisCacheConfig {
    String DEFAULT = "default";
    NamedCacheConfig DEFAULT_CONFIG = new NamedCacheConfig(null, null);

    default Map<String, NamedCacheConfig> redis() {
        return Map.of();
    }

    default NamedCacheConfig getByName(@Nonnull String name) {
        final NamedCacheConfig defaultConfig = this.redis().getOrDefault(DEFAULT, DEFAULT_CONFIG);
        final NamedCacheConfig namedConfig = this.redis().get(name);
        if (namedConfig == null) {
            return defaultConfig;
        }

        return new NamedCacheConfig(
            namedConfig.expireAfterWrite == null ? defaultConfig.expireAfterWrite : namedConfig.expireAfterWrite,
            namedConfig.expireAfterAccess == null ? defaultConfig.expireAfterAccess : namedConfig.expireAfterAccess
        );
    }


    @ConfigValueExtractor
    record NamedCacheConfig(
        @Nullable Duration expireAfterWrite,
        @Nullable Duration expireAfterAccess
    ) {}
}
