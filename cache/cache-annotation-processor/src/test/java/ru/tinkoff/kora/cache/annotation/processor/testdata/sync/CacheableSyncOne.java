package ru.tinkoff.kora.cache.annotation.processor.testdata.sync;

import ru.tinkoff.kora.cache.annotation.CacheInvalidate;
import ru.tinkoff.kora.cache.annotation.CachePut;
import ru.tinkoff.kora.cache.annotation.Cacheable;
import ru.tinkoff.kora.cache.annotation.processor.testcache.DummyCache1;

import java.math.BigDecimal;

public class CacheableSyncOne {

    public String value = "1";

    @Cacheable(DummyCache1.class)
    public String getValue(String arg1) {
        return value;
    }

    @CachePut(value = DummyCache1.class, parameters = {"arg1"})
    public String putValue(BigDecimal arg2, String arg3, String arg1) {
        return value;
    }

    @CacheInvalidate(DummyCache1.class)
    public void evictValue(String arg1) {

    }

    @CacheInvalidate(value = DummyCache1.class, invalidateAll = true)
    public void evictAll() {

    }
}
