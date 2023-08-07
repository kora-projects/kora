package ru.tinkoff.kora.cache.annotation.processor;

import org.jetbrains.annotations.Nullable;
import ru.tinkoff.kora.cache.caffeine.CaffeineCacheConfig;

import java.time.Duration;

final class CacheRunner {

    private CacheRunner() { }

    public static CaffeineCacheConfig getConfig() {
        return new CaffeineCacheConfig() {
            @Nullable
            @Override
            public Duration expireAfterWrite() {
                return null;
            }

            @Nullable
            @Override
            public Duration expireAfterAccess() {
                return null;
            }

            @Nullable
            @Override
            public Integer initialSize() {
                return null;
            }
        };
    }
}
