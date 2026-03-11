package io.koraframework.cache.annotation.processor.testcache;

import io.koraframework.cache.annotation.Cache;
import io.koraframework.cache.caffeine.CaffeineCache;
import io.koraframework.cache.redis.RedisCache;

@Cache("dummy12")
public interface DummyCache12 extends RedisCache<String, String> {

}
