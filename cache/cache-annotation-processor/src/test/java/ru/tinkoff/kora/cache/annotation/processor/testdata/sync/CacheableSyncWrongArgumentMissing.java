package ru.tinkoff.kora.cache.annotation.processor.testdata.sync;

import ru.tinkoff.kora.cache.annotation.CachePut;
import ru.tinkoff.kora.cache.annotation.Cacheable;
import ru.tinkoff.kora.cache.annotation.processor.testcache.DummyCache2;

import java.math.BigDecimal;

public class CacheableSyncWrongArgumentMissing {

    public String value = "1";

    @Cacheable(DummyCache2.class)
    public String getValue(String arg1, BigDecimal arg2) {
        return value;
    }

    @CachePut(value = DummyCache2.class, parameters = {"arg1", "arg4"})
    public String putValue(BigDecimal arg2, String arg3, String arg1) {
        return value;
    }
}
