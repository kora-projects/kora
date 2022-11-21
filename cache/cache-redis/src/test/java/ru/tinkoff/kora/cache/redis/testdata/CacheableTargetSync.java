package ru.tinkoff.kora.cache.redis.testdata;

import ru.tinkoff.kora.cache.annotation.CacheInvalidate;
import ru.tinkoff.kora.cache.annotation.CachePut;
import ru.tinkoff.kora.cache.annotation.Cacheable;
import ru.tinkoff.kora.cache.redis.RedisCacheManager;
import ru.tinkoff.kora.common.Component;

import java.math.BigDecimal;

@Component
public class CacheableTargetSync {

    public String number = "1";

    @Cacheable(name = "sync_cache", tags = RedisCacheManager.class)
    public Box getValue(String arg1, BigDecimal arg2) {
        return new Box(number, BigDecimal.TEN);
    }

    @CachePut(name = "sync_cache", tags = RedisCacheManager.class, parameters = {"arg1", "arg2"})
    public Box putValue(BigDecimal arg2, String arg3, String arg1) {
        return new Box(number, BigDecimal.TEN);
    }

    @CacheInvalidate(name = "sync_cache", tags = RedisCacheManager.class)
    public void evictValue(String arg1, BigDecimal arg2) {

    }

    @CacheInvalidate(name = "sync_cache", tags = RedisCacheManager.class, invalidateAll = true)
    public void evictAll() {

    }
}
