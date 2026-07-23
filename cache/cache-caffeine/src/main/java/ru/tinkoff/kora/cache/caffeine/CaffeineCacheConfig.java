package ru.tinkoff.kora.cache.caffeine;


import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.time.Duration;

@ConfigValueExtractor
public interface CaffeineCacheConfig {

    /**
     * @return Time after which a value is removed from the cache, counted from the moment the value was written.
     */
    @Nullable
    Duration expireAfterWrite();

    /**
     * @return Time after which a value is removed from the cache, counted from the moment the value was read.
     */
    @Nullable
    Duration expireAfterAccess();

    /**
     * @return Maximum cache size, upon reaching it or slightly earlier the least relevant values are evicted.
     */
    default Long maximumSize() {
        return 100_000L;
    }

    /**
     * @return Initial cache size, helps to avoid resizing when the number of values grows quickly.
     */
    @Nullable
    Integer initialSize();
}
