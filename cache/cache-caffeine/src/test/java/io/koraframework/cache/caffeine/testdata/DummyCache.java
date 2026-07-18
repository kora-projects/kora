package io.koraframework.cache.caffeine.testdata;

import io.koraframework.cache.caffeine.AbstractCaffeineCache;
import io.koraframework.cache.caffeine.CaffeineCacheConfig;
import io.koraframework.cache.caffeine.CaffeineCacheFactory;
import io.koraframework.cache.caffeine.telemetry.CaffeineCacheTelemetryFactory;

public final class DummyCache extends AbstractCaffeineCache<String, String> {

    public DummyCache(CaffeineCacheConfig config, CaffeineCacheFactory factory, CaffeineCacheTelemetryFactory telemetryFactory) {
        super("dummy", config, factory, telemetryFactory);
    }
}
