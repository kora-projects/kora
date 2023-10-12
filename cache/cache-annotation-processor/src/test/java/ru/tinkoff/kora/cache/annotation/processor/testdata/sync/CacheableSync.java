package ru.tinkoff.kora.cache.annotation.processor.testdata.sync;

import ru.tinkoff.kora.cache.annotation.CacheInvalidate;
import ru.tinkoff.kora.cache.annotation.CachePut;
import ru.tinkoff.kora.cache.annotation.Cacheable;
import ru.tinkoff.kora.cache.annotation.processor.testcache.DummyCache21;

import java.math.BigDecimal;
import java.util.Optional;

public class CacheableSync {

    public String value = "1";

    @Cacheable(DummyCache21.class)
    public String getValue(String arg1, BigDecimal arg2) {
        return value;
    }

    @Cacheable(DummyCache21.class)
    public Optional<String> getValueOptional(String arg1, BigDecimal arg2) {
        return value.describeConstable();
    }

    @CachePut(value = DummyCache21.class, parameters = {"arg1", "arg2"})
    public String putValue(BigDecimal arg2, String arg3, String arg1) {
        return value;
    }

    @CachePut(value = DummyCache21.class, parameters = {"arg1", "arg2"})
    public Optional<String> putValueOptional(BigDecimal arg2, String arg3, String arg1) {
        return Optional.ofNullable(value);
    }

    @CacheInvalidate(DummyCache21.class)
    public void evictValue(String arg1, BigDecimal arg2) {

    }

    @CacheInvalidate(value = DummyCache21.class, invalidateAll = true)
    public void evictAll() {

    }
}
