package ru.tinkoff.kora.cache.redis.testdata;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.cache.annotation.CacheInvalidate;
import ru.tinkoff.kora.cache.annotation.CachePut;
import ru.tinkoff.kora.cache.annotation.Cacheable;
import ru.tinkoff.kora.cache.redis.RedisCacheManager;
import ru.tinkoff.kora.common.Component;

import java.math.BigDecimal;

@Component
public class CacheableTargetMono {

    public String number = "1";

    @Cacheable(name = "mono_cache", tags = RedisCacheManager.class)
    public Mono<Box> getValue(String arg1, BigDecimal arg2) {
        return Mono.just(new Box(number, BigDecimal.TEN));
    }

    @CachePut(name = "mono_cache", tags = RedisCacheManager.class, parameters = {"arg1", "arg2"})
    public Mono<Box> putValue(BigDecimal arg2, String arg3, String arg1) {
        return Mono.just(new Box(number, BigDecimal.TEN));
    }

    @CacheInvalidate(name = "mono_cache", tags = RedisCacheManager.class)
    public Mono<Void> evictValue(String arg1, BigDecimal arg2) {
        return Mono.empty();
    }

    @CacheInvalidate(name = "mono_cache", tags = RedisCacheManager.class, invalidateAll = true)
    public Mono<Void> evictAll() {
        return Mono.empty();
    }
}
