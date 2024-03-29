package ru.tinkoff.kora.cache.annotation.processor.testdata.reactive.mono;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.cache.annotation.CacheInvalidate;
import ru.tinkoff.kora.cache.annotation.CachePut;
import ru.tinkoff.kora.cache.annotation.Cacheable;
import ru.tinkoff.kora.cache.annotation.processor.testcache.DummyCache21;
import ru.tinkoff.kora.cache.annotation.processor.testcache.DummyCache22;

import java.math.BigDecimal;

public class CacheableMonoMany {

    public String value = "1";

    @Cacheable(DummyCache21.class)
    @Cacheable(DummyCache22.class)
    public Mono<String> getValue(String arg1, BigDecimal arg2) {
        return Mono.just(value);
    }

    @CachePut(value = DummyCache21.class, parameters = {"arg1", "arg2"})
    @CachePut(value = DummyCache22.class, parameters = {"arg1", "arg2"})
    public Mono<String> putValue(BigDecimal arg2, String arg3, String arg1) {
        return Mono.just(value);
    }

    @CacheInvalidate(DummyCache21.class)
    @CacheInvalidate(DummyCache22.class)
    public Mono<String> evictValue(String arg1, BigDecimal arg2) {
        return Mono.just("1");
    }

    @CacheInvalidate(value = DummyCache21.class, invalidateAll = true)
    @CacheInvalidate(value = DummyCache22.class, invalidateAll = true)
    public Mono<String> evictAll() {
        return Mono.just("1");
    }
}
