package ru.tinkoff.kora.cache.annotation.processor.testcache;

import ru.tinkoff.kora.cache.annotation.Cache;
import ru.tinkoff.kora.cache.caffeine.CaffeineCache;
import ru.tinkoff.kora.cache.redis.RedisCache;

import java.math.BigDecimal;

@Cache("dummy22")
public interface DummyCache22 extends RedisCache<DummyCache22.Key, String> {

    record Key(String k1, BigDecimal k2) {}
}
