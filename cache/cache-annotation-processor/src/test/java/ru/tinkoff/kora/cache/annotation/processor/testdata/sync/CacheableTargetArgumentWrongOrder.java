package ru.tinkoff.kora.cache.annotation.processor.testdata.sync;

import ru.tinkoff.kora.cache.annotation.CachePut;
import ru.tinkoff.kora.cache.annotation.Cacheable;
import ru.tinkoff.kora.cache.annotation.processor.testcache.DummyCacheManager;

import java.math.BigDecimal;

public class CacheableTargetArgumentWrongOrder {

    public String value = "1";

    @Cacheable(name = "sync_cache", tags = DummyCacheManager.class)
    public String getValue(String arg1, BigDecimal arg2) {
        return value;
    }

    @CachePut(name = "sync_cache", tags = DummyCacheManager.class, parameters = {"arg2", "arg1"})
    public String putValue(BigDecimal arg2, String arg3, String arg1) {
        return value;
    }
}
