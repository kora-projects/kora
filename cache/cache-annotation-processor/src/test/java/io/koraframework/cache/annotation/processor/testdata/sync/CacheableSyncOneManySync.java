package io.koraframework.cache.annotation.processor.testdata.sync;

import io.koraframework.cache.annotation.CacheInvalidate;
import io.koraframework.cache.annotation.CachePut;
import io.koraframework.cache.annotation.Cacheable;
import io.koraframework.cache.annotation.processor.testcache.DummyCache11;
import io.koraframework.cache.annotation.processor.testcache.DummyCache13;

import java.math.BigDecimal;

public class CacheableSyncOneManySync {

    public String value = "1";

    @Cacheable(DummyCache11.class)
    @Cacheable(DummyCache13.class)
    public String getValue(String arg1) {
        return value;
    }

    @CachePut(value = DummyCache11.class, parameters = {"arg1"})
    @CachePut(value = DummyCache13.class, parameters = {"arg1"})
    public String putValue(BigDecimal arg2, String arg3, String arg1) {
        return value;
    }

    @CacheInvalidate(DummyCache11.class)
    @CacheInvalidate(DummyCache13.class)
    public void evictValue(String arg1) {

    }

    @CacheInvalidate(value = DummyCache11.class, invalidateAll = true)
    @CacheInvalidate(value = DummyCache13.class, invalidateAll = true)
    public void evictAll() {

    }
}
