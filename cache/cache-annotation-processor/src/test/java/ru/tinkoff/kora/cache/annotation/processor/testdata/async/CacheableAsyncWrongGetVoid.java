package ru.tinkoff.kora.cache.annotation.processor.testdata.async;

import ru.tinkoff.kora.cache.annotation.CachePut;
import ru.tinkoff.kora.cache.annotation.Cacheable;
import ru.tinkoff.kora.cache.annotation.processor.testcache.DummyCache21;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class CacheableAsyncWrongGetVoid {

    public String value = "1";

    @Cacheable(DummyCache21.class)
    public CompletionStage<Void> getValue(String arg1, BigDecimal arg2) {
        return CompletableFuture.completedFuture(null);
    }

    @CachePut(DummyCache21.class)
    public String putValue(String arg1, BigDecimal arg2) {
        return value;
    }
}
