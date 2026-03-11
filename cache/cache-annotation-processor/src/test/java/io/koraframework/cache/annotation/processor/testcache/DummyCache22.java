package io.koraframework.cache.annotation.processor.testcache;

import io.koraframework.cache.annotation.Cache;
import io.koraframework.cache.caffeine.CaffeineCache;
import io.koraframework.cache.redis.RedisCache;

import java.math.BigDecimal;

@Cache("dummy22")
public interface DummyCache22 extends RedisCache<DummyCache22.Key, String> {

    record Key(String k1, BigDecimal k2) {}
}
