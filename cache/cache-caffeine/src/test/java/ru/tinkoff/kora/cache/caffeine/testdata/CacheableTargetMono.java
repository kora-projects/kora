package ru.tinkoff.kora.cache.caffeine.testdata;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.cache.annotation.CacheInvalidate;
import ru.tinkoff.kora.cache.annotation.CachePut;
import ru.tinkoff.kora.cache.annotation.Cacheable;
import ru.tinkoff.kora.cache.caffeine.CaffeineCacheManager;
import ru.tinkoff.kora.common.Component;

import java.math.BigDecimal;

@Component
public class CacheableTargetMono {

    public static final String CACHE_NAME = "mono_cache";

    public String value = "1";

    @Cacheable(name = CACHE_NAME, tags = CaffeineCacheManager.class)
    public Mono<String> getValue(String arg1, BigDecimal arg2) {
        return Mono.just(value);
    }

    @CachePut(name = CACHE_NAME, tags = CaffeineCacheManager.class, parameters = {"arg1", "arg2"})
    public Mono<String> putValue(BigDecimal arg2, String arg3, String arg1) {
        return Mono.just(value);
    }

    @CacheInvalidate(name = CACHE_NAME, tags = CaffeineCacheManager.class)
    public Mono<Void> evictValue(String arg1, BigDecimal arg2) {
        return Mono.empty();
    }

    @CacheInvalidate(name = CACHE_NAME, tags = CaffeineCacheManager.class, invalidateAll = true)
    public Mono<Void> evictAll() {
        return Mono.empty();
    }
}
