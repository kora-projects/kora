package ru.tinkoff.kora.cache.redis;


import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import jakarta.annotation.Nullable;
import java.time.Duration;

@ConfigValueExtractor
public interface RedisCacheConfig {

    @Nullable
    Duration expireAfterWrite();

    @Nullable
    Duration expireAfterAccess();
}
