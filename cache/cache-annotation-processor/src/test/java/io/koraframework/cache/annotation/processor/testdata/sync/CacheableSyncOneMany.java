package io.koraframework.cache.annotation.processor.testdata.sync;

import io.koraframework.cache.annotation.CacheInvalidate;
import io.koraframework.cache.annotation.CacheInvalidateAll;
import io.koraframework.cache.annotation.CachePut;
import io.koraframework.cache.annotation.Cacheable;
import io.koraframework.cache.annotation.processor.testcache.DummyCache11;
import io.koraframework.cache.annotation.processor.testcache.DummyCache12;

import java.math.BigDecimal;

public class CacheableSyncOneMany {

    public String value = "1";

    @Cacheable(DummyCache11.class)
    @Cacheable(DummyCache12.class)
    public String getValue(String arg1) {
        return value;
    }

    @CachePut(value = DummyCache11.class, args = {"arg1"})
    @CachePut(value = DummyCache12.class, args = {"arg1"})
    public String putValue(BigDecimal arg2, String arg3, String arg1) {
        return value;
    }

    @CacheInvalidate(DummyCache11.class)
    @CacheInvalidate(DummyCache12.class)
    public void evictValue(String arg1) {

    }

    @CacheInvalidateAll(DummyCache11.class)
    @CacheInvalidateAll(DummyCache12.class)
    public void evictAll() {

    }
}
