package ru.tinkoff.kora.cache.caffeine.testdata;

import ru.tinkoff.kora.cache.annotation.CacheInvalidate;
import ru.tinkoff.kora.cache.annotation.CachePut;
import ru.tinkoff.kora.cache.annotation.Cacheable;
import ru.tinkoff.kora.cache.caffeine.CaffeineCacheManager;
import ru.tinkoff.kora.common.Component;

import java.math.BigDecimal;

@Component
public class CacheableTargetSync {

    public static final String CACHE_NAME = "sync_cache";

    public String value = "1";

    @Cacheable(name = CACHE_NAME, tags = CaffeineCacheManager.class)
    public String getValue(String arg1, BigDecimal arg2) {
        return value;
    }

    @CachePut(name = CACHE_NAME, tags = CaffeineCacheManager.class, parameters = {"arg1", "arg2"})
    public String putValue(BigDecimal arg2, String arg3, String arg1) {
        return value;
    }

    @CacheInvalidate(name = CACHE_NAME, tags = CaffeineCacheManager.class)
    public void evictValue(String arg1, BigDecimal arg2) {

    }

    @CacheInvalidate(name = CACHE_NAME, tags = CaffeineCacheManager.class, invalidateAll = true)
    public void evictAll() {

    }
}
