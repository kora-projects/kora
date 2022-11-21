package ru.tinkoff.kora.cache.annotation.processor.testdata.reactive.flux;

import reactor.core.publisher.Flux;
import ru.tinkoff.kora.cache.annotation.CachePut;
import ru.tinkoff.kora.cache.annotation.Cacheable;
import ru.tinkoff.kora.cache.annotation.processor.testcache.DummyCacheManager;

import java.math.BigDecimal;

public class CacheableTargetGetFlux {

    public String value = "1";

    @Cacheable(name = "flux_cache", tags = DummyCacheManager.class)
    public Flux<String> getValue(String arg1, BigDecimal arg2) {
        return Flux.just(value);
    }

    @CachePut(name = "flux_cache", tags = DummyCacheManager.class)
    public String putValue(String arg1, BigDecimal arg2) {
        return value;
    }
}
