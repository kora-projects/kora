package ru.tinkoff.kora.cache.annotation.processor.testdata.reactive.mono;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.cache.annotation.CachePut;
import ru.tinkoff.kora.cache.annotation.Cacheable;
import ru.tinkoff.kora.cache.annotation.processor.testcache.DummyCache2;

import java.math.BigDecimal;

public class CacheableMonoWrongPutVoid {

    public String value = "1";

    @Cacheable(DummyCache2.class)
    public String getValue(String arg1, BigDecimal arg2) {
        return value;
    }

    @CachePut(value = DummyCache2.class, parameters = {"arg1", "arg2"})
    public Mono<Void> putValue(String arg1, BigDecimal arg2) {
        return Mono.empty();
    }
}
