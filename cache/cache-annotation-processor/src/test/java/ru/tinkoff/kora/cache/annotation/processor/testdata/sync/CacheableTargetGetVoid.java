package ru.tinkoff.kora.cache.annotation.processor.testdata.sync;

import ru.tinkoff.kora.cache.annotation.CachePut;
import ru.tinkoff.kora.cache.annotation.Cacheable;
import ru.tinkoff.kora.cache.annotation.processor.testcache.DummyCacheManager;

import java.math.BigDecimal;

public class CacheableTargetGetVoid {

    public String value = "1";

    @Cacheable(name = "sync_cache", tags = DummyCacheManager.class)
    public void getValue(String arg1, BigDecimal arg2) {

    }

    @CachePut(name = "sync_cache", tags = DummyCacheManager.class)
    public String putValue(String arg1, BigDecimal arg2) {
        return value;
    }
}
