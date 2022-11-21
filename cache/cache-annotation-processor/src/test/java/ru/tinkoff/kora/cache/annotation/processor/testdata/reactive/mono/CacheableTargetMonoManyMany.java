package ru.tinkoff.kora.cache.annotation.processor.testdata.reactive.mono;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.cache.annotation.CacheInvalidate;
import ru.tinkoff.kora.cache.annotation.CachePut;
import ru.tinkoff.kora.cache.annotation.Cacheable;
import ru.tinkoff.kora.cache.annotation.processor.testcache.DummyCacheManager;

import java.math.BigDecimal;

public class CacheableTargetMonoManyMany {

    public String value = "1";

    @Cacheable(name = "mono_cache", tags = DummyCacheManager.class)
    @Cacheable(name = "mono_cache_2", tags = DummyCacheManager.class)
    @Cacheable(name = "mono_cache_3", tags = DummyCacheManager.class)
    public Mono<String> getValue(String arg1, BigDecimal arg2) {
        return Mono.just(value);
    }

    @CachePut(name = "mono_cache", tags = DummyCacheManager.class, parameters = {"arg1", "arg2"})
    @CachePut(name = "mono_cache_2", tags = DummyCacheManager.class, parameters = {"arg1", "arg2"})
    @CachePut(name = "mono_cache_3", tags = DummyCacheManager.class, parameters = {"arg1", "arg2"})
    public Mono<String> putValue(BigDecimal arg2, String arg3, String arg1) {
        return Mono.just(value);
    }

    @CacheInvalidate(name = "mono_cache", tags = DummyCacheManager.class)
    @CacheInvalidate(name = "mono_cache_2", tags = DummyCacheManager.class)
    @CacheInvalidate(name = "mono_cache_3", tags = DummyCacheManager.class)
    public Mono<Void> evictValue(String arg1, BigDecimal arg2) {
        return Mono.empty();
    }

    @CacheInvalidate(name = "mono_cache", tags = DummyCacheManager.class, invalidateAll = true)
    @CacheInvalidate(name = "mono_cache_2", tags = DummyCacheManager.class, invalidateAll = true)
    @CacheInvalidate(name = "mono_cache_3", tags = DummyCacheManager.class, invalidateAll = true)
    public Mono<Void> evictAll() {
        return Mono.empty();
    }
}
