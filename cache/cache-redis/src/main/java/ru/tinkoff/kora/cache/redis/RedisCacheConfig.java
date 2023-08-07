package ru.tinkoff.kora.cache.redis;


import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import javax.annotation.Nullable;
import java.time.Duration;

@ConfigValueExtractor
public interface RedisCacheConfig {

    @Nullable
    Duration expireAfterWrite();

    @Nullable
    Duration expireAfterAccess();
}
