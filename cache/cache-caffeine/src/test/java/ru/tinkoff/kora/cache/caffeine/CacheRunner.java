package ru.tinkoff.kora.cache.caffeine;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Assertions;
import ru.tinkoff.kora.cache.caffeine.testdata.DummyCache;

import java.time.Duration;

abstract class CacheRunner extends Assertions implements CaffeineCacheModule {

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

    protected DummyCache createCache() {
        try {
            return new DummyCache(getConfig(), caffeineCacheFactory(null), caffeineCacheTelemetry(null, null));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
