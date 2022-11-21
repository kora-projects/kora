package ru.tinkoff.kora.cache.annotation.processor.testdata.reactive.flux;

import reactor.core.publisher.Flux;
import ru.tinkoff.kora.cache.annotation.CachePut;
import ru.tinkoff.kora.cache.annotation.Cacheable;
import ru.tinkoff.kora.cache.annotation.processor.testcache.DummyCacheManager;

import java.math.BigDecimal;

public class CacheableTargetPutFlux {

    public String value = "1";

    @Cacheable(name = "flux_cache", tags = DummyCacheManager.class)
    public String getValue(String arg1, BigDecimal arg2) {
        return value;
    }

    @CachePut(name = "flux_cache", tags = DummyCacheManager.class, parameters = {"arg1", "arg2"})
    public Flux<String> putValue(String arg1, BigDecimal arg2) {
        return Flux.just(value);
    }
}
