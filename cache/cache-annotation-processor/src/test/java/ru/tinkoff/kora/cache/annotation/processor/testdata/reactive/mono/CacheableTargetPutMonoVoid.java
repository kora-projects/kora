package ru.tinkoff.kora.cache.annotation.processor.testdata.reactive.mono;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.cache.annotation.CachePut;
import ru.tinkoff.kora.cache.annotation.Cacheable;
import ru.tinkoff.kora.cache.annotation.processor.testcache.DummyCacheManager;

import java.math.BigDecimal;

public class CacheableTargetPutMonoVoid {

    public String value = "1";

    @Cacheable(name = "mono_cache", tags = DummyCacheManager.class)
    public String getValue(String arg1, BigDecimal arg2) {
        return value;
    }

    @CachePut(name = "mono_cache", tags = DummyCacheManager.class, parameters = {"arg1", "arg2"})
    public Mono<Void> putValue(String arg1, BigDecimal arg2) {
        return Mono.empty();
    }
}
