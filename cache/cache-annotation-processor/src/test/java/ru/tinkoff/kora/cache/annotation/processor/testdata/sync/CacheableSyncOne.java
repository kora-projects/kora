package ru.tinkoff.kora.cache.annotation.processor.testdata.sync;

import ru.tinkoff.kora.cache.annotation.CacheInvalidate;
import ru.tinkoff.kora.cache.annotation.CachePut;
import ru.tinkoff.kora.cache.annotation.Cacheable;
import ru.tinkoff.kora.cache.annotation.processor.testcache.DummyCache11;

import java.math.BigDecimal;

public class CacheableSyncOne {

    public String value = "1";

    @Cacheable(DummyCache11.class)
    public String getValue(String arg1) {
        return value;
    }

    @CachePut(value = DummyCache11.class, parameters = {"arg1"})
    public String putValue(BigDecimal arg2, String arg3, String arg1) {
        return value;
    }

    @CacheInvalidate(DummyCache11.class)
    public void evictValue(String arg1) {

    }

    @CacheInvalidate(value = DummyCache11.class, invalidateAll = true)
    public void evictAll() {

    }
}
