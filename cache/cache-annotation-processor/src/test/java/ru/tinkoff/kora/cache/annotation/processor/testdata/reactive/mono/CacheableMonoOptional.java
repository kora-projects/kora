package ru.tinkoff.kora.cache.annotation.processor.testdata.reactive.mono;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.cache.annotation.CacheInvalidate;
import ru.tinkoff.kora.cache.annotation.CachePut;
import ru.tinkoff.kora.cache.annotation.Cacheable;
import ru.tinkoff.kora.cache.annotation.processor.testcache.DummyCache21;

import java.math.BigDecimal;
import java.util.Optional;

public class CacheableMonoOptional {

    public String value = "1";

    @Cacheable(DummyCache21.class)
    public Mono<Optional<String>> getValue(String arg1, BigDecimal arg2) {
        return Mono.just(Optional.ofNullable(value));
    }

    @CachePut(value = DummyCache21.class, parameters = {"arg1", "arg2"})
    public Mono<Optional<String>> putValue(BigDecimal arg2, String arg3, String arg1) {
        return Mono.just(Optional.ofNullable(value));
    }

    @CacheInvalidate(DummyCache21.class)
    public Mono<Optional<String>> evictValue(String arg1, BigDecimal arg2) {
        return Mono.just(Optional.ofNullable(value));
    }

    @CacheInvalidate(value = DummyCache21.class, invalidateAll = true)
    public Mono<Optional<String>> evictAll() {
        return Mono.just(Optional.ofNullable(value));
    }
}
