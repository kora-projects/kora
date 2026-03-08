package io.koraframework.cache.annotation.processor.testcache;

import io.koraframework.cache.annotation.Cache;
import io.koraframework.cache.caffeine.CaffeineCache;
import io.koraframework.cache.redis.RedisCache;

@Cache("dummy13")
public interface DummyCache13 extends CaffeineCache<String, String> {

}
