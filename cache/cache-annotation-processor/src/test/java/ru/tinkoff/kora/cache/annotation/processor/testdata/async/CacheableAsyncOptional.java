package ru.tinkoff.kora.cache.annotation.processor.testdata.async;

import ru.tinkoff.kora.cache.annotation.CacheInvalidate;
import ru.tinkoff.kora.cache.annotation.CachePut;
import ru.tinkoff.kora.cache.annotation.Cacheable;
import ru.tinkoff.kora.cache.annotation.processor.testcache.DummyCache21;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class CacheableAsyncOptional {

    public String value = "1";

    @Cacheable(DummyCache21.class)
    public CompletionStage<Optional<String>> getValue(String arg1, BigDecimal arg2) {
        return CompletableFuture.completedFuture(Optional.ofNullable(value));
    }

    @CachePut(value = DummyCache21.class, parameters = {"arg1", "arg2"})
    public CompletionStage<Optional<String>> putValue(BigDecimal arg2, String arg3, String arg1) {
        return CompletableFuture.completedFuture(Optional.ofNullable(value));
    }

    @CacheInvalidate(DummyCache21.class)
    public CompletionStage<Optional<String>> evictValue(String arg1, BigDecimal arg2) {
        return CompletableFuture.completedFuture(Optional.ofNullable(value));
    }

    @CacheInvalidate(value = DummyCache21.class, invalidateAll = true)
    public CompletionStage<Optional<String>> evictAll() {
        return CompletableFuture.completedFuture(Optional.ofNullable(value));
    }
}
