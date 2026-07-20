package io.koraframework.cache.annotation.processor.testdata.sync;

import io.koraframework.cache.annotation.CacheInvalidate;
import io.koraframework.cache.annotation.CacheInvalidateAll;
import io.koraframework.cache.annotation.CacheMode;
import io.koraframework.cache.annotation.CachePut;
import io.koraframework.cache.annotation.Cacheable;
import io.koraframework.cache.annotation.processor.testcache.DummyCache21;
import io.koraframework.cache.annotation.processor.testcache.DummyCache22;

import java.math.BigDecimal;

public class CacheableAsync {

    @Cacheable(value = DummyCache22.class, mode = CacheMode.ASYNC)
    public String getValue(String arg1, BigDecimal arg2) {
        return "1";
    }

    @Cacheable(value = DummyCache21.class, mode = CacheMode.ASYNC)
    public String getCaffeineValue(String arg1, BigDecimal arg2) {
        return "1";
    }

    @CachePut(value = DummyCache22.class, args = {"arg1", "arg2"}, mode = CacheMode.ASYNC)
    public String putValue(String arg1, BigDecimal arg2) {
        return "1";
    }

    @CacheInvalidate(value = DummyCache22.class, mode = CacheMode.ASYNC)
    public void evictValue(String arg1, BigDecimal arg2) {}

    @CacheInvalidateAll(value = DummyCache22.class, mode = CacheMode.ASYNC)
    public void evictAll() {}
}
