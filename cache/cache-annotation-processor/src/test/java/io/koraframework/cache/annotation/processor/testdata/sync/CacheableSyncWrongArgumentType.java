package io.koraframework.cache.annotation.processor.testdata.sync;

import io.koraframework.cache.annotation.CachePut;
import io.koraframework.cache.annotation.Cacheable;
import io.koraframework.cache.annotation.processor.testcache.DummyCache21;

import java.math.BigDecimal;

public class CacheableSyncWrongArgumentType {

    public String value = "1";

    @Cacheable(DummyCache21.class)
    public String getValue(String arg1, BigDecimal arg2) {
        return value;
    }

    @CachePut(value = DummyCache21.class, args = {"arg1", "arg3"})
    public String putValue(BigDecimal arg2, String arg3, String arg1) {
        return value;
    }
}
