package ru.tinkoff.kora.cache.annotation.processor.testcache;

import ru.tinkoff.kora.cache.annotation.Cache;
import ru.tinkoff.kora.cache.caffeine.CaffeineCache;
import ru.tinkoff.kora.cache.redis.RedisCache;

@Cache("dummy2")
public interface DummyCache12 extends RedisCache<String, String> {

}
