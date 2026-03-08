package io.koraframework.cache.annotation.processor.testdata.sync;

import io.koraframework.cache.annotation.CachePut;
import io.koraframework.cache.annotation.Cacheable;
import io.koraframework.cache.annotation.processor.testcache.DummyCache21;

import java.math.BigDecimal;

public class CacheableSyncWrongGetVoid {

    public String value = "1";

    @Cacheable(DummyCache21.class)
    public void getValue(String arg1, BigDecimal arg2) {

    }

    @CachePut(DummyCache21.class)
    public String putValue(String arg1, BigDecimal arg2) {
        return value;
    }
}
