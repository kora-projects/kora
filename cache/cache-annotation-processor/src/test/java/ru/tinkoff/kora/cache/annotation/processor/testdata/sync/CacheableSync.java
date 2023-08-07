package ru.tinkoff.kora.cache.annotation.processor.testdata.sync;

import ru.tinkoff.kora.cache.annotation.CacheInvalidate;
import ru.tinkoff.kora.cache.annotation.CachePut;
import ru.tinkoff.kora.cache.annotation.Cacheable;
import ru.tinkoff.kora.cache.annotation.processor.testcache.DummyCache2;

import java.math.BigDecimal;

public class CacheableSync {

    public String value = "1";

    @Cacheable(DummyCache2.class)
    public String getValue(String arg1, BigDecimal arg2) {
        return value;
    }

    @CachePut(value = DummyCache2.class, parameters = {"arg1", "arg2"})
    public String putValue(BigDecimal arg2, String arg3, String arg1) {
        return value;
    }

    @CacheInvalidate(DummyCache2.class)
    public void evictValue(String arg1, BigDecimal arg2) {

    }

    @CacheInvalidate(value = DummyCache2.class, invalidateAll = true)
    public void evictAll() {

    }
}
