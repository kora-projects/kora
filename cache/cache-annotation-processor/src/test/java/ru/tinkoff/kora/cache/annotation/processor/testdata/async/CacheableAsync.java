package ru.tinkoff.kora.cache.annotation.processor.testdata.async;

import ru.tinkoff.kora.cache.annotation.CacheInvalidate;
import ru.tinkoff.kora.cache.annotation.CachePut;
import ru.tinkoff.kora.cache.annotation.Cacheable;
import ru.tinkoff.kora.cache.annotation.processor.testcache.DummyCache21;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class CacheableAsync {

    public String value = "1";

    @Cacheable(DummyCache21.class)
    public CompletionStage<String> getValue(String arg1, BigDecimal arg2) {
        return CompletableFuture.completedFuture(value);
    }

    @CachePut(value = DummyCache21.class, parameters = {"arg1", "arg2"})
    public CompletionStage<String> putValue(BigDecimal arg2, String arg3, String arg1) {
        return CompletableFuture.completedFuture(value);
    }

    @CacheInvalidate(DummyCache21.class)
    public CompletionStage<Void> evictValue(String arg1, BigDecimal arg2) {
        return CompletableFuture.completedFuture(null);
    }

    @CacheInvalidate(value = DummyCache21.class, invalidateAll = true)
    public CompletionStage<Void> evictAll() {
        return CompletableFuture.completedFuture(null);
    }
}
