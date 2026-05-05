package io.koraframework.cache.caffeine.testdata;

import io.koraframework.cache.caffeine.AbstractCaffeineCache;
import io.koraframework.cache.caffeine.CaffeineCacheConfig;
import io.koraframework.cache.caffeine.CaffeineCacheFactory;

public final class DummyCache extends AbstractCaffeineCache<String, String> {

    public DummyCache(CaffeineCacheConfig config, CaffeineCacheFactory factory) {
        super("dummy", "dummy", config, factory);
    }
}
