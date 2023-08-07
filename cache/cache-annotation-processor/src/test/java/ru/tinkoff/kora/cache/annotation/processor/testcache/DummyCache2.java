package ru.tinkoff.kora.cache.annotation.processor.testcache;

import ru.tinkoff.kora.cache.CacheKey;
import ru.tinkoff.kora.cache.annotation.Cache;
import ru.tinkoff.kora.cache.caffeine.CaffeineCache;

import java.math.BigDecimal;

@Cache("dummy")
public interface DummyCache2 extends CaffeineCache<CacheKey.Key2<String, BigDecimal>, String> {

}
