package ru.tinkoff.kora.cache.annotation.processor.testdata.sync;

import ru.tinkoff.kora.cache.annotation.CachePut;
import ru.tinkoff.kora.cache.annotation.Cacheable;
import ru.tinkoff.kora.cache.annotation.processor.testcache.DummyCache2;

import java.math.BigDecimal;

public class CacheableSyncWrongGetVoid {

    public String value = "1";

    @Cacheable(DummyCache2.class)
    public void getValue(String arg1, BigDecimal arg2) {

    }

    @CachePut(DummyCache2.class)
    public String putValue(String arg1, BigDecimal arg2) {
        return value;
    }
}
