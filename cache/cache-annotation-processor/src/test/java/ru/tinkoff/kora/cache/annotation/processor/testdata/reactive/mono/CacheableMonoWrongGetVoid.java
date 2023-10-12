package ru.tinkoff.kora.cache.annotation.processor.testdata.reactive.mono;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.cache.annotation.CachePut;
import ru.tinkoff.kora.cache.annotation.Cacheable;
import ru.tinkoff.kora.cache.annotation.processor.testcache.DummyCache21;

import java.math.BigDecimal;

public class CacheableMonoWrongGetVoid {

    public String value = "1";

    @Cacheable(DummyCache21.class)
    public Mono<Void> getValue(String arg1, BigDecimal arg2) {
        return Mono.empty();
    }

    @CachePut(DummyCache21.class)
    public String putValue(String arg1, BigDecimal arg2) {
        return value;
    }
}
