package ru.tinkoff.kora.cache.annotation.processor.testdata.sync;

import ru.tinkoff.kora.cache.annotation.CacheInvalidate;
import ru.tinkoff.kora.cache.annotation.CachePut;
import ru.tinkoff.kora.cache.annotation.Cacheable;
import ru.tinkoff.kora.cache.annotation.processor.testcache.DummyCacheManager;

import java.math.BigDecimal;

public class CacheableTargetSync {

    public String value = "1";

    @Cacheable(name = "sync_cache", tags = DummyCacheManager.class)
    public String getValue(String arg1, BigDecimal arg2) {
        return value;
    }

    @CachePut(name = "sync_cache", tags = DummyCacheManager.class, parameters = {"arg1", "arg2"})
    public String putValue(BigDecimal arg2, String arg3, String arg1) {
        return value;
    }

    @CacheInvalidate(name = "sync_cache", tags = DummyCacheManager.class)
    public void evictValue(String arg1, BigDecimal arg2) {

    }

    @CacheInvalidate(name = "sync_cache", tags = DummyCacheManager.class, invalidateAll = true)
    public void evictAll() {

    }
}
