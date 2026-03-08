package io.koraframework.cache.annotation.processor.testcache;

import io.koraframework.cache.annotation.Cache;
import io.koraframework.cache.caffeine.CaffeineCache;

@Cache("dummy11")
public interface DummyCache11 extends CaffeineCache<String, String> {

}
