package io.koraframework.cache.annotation.processor.testcache;

import io.koraframework.cache.annotation.Cache;
import io.koraframework.cache.caffeine.CaffeineCache;

import java.math.BigDecimal;

@Cache("dummy21")
public interface DummyCache21 extends CaffeineCache<DummyCache21.Key, String> {

    record Key(String k1, BigDecimal k2) {}
}
