package ru.tinkoff.kora.cache.caffeine;


import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.time.Duration;

@ConfigValueExtractor
public interface CaffeineCacheConfig {

    @Nullable
    Duration expireAfterWrite();

    @Nullable
    Duration expireAfterAccess();

    default Long maximumSize() {
        return 100_000L;
    }

    @Nullable
    Integer initialSize();
}
