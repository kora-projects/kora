package ru.tinkoff.kora.cache.caffeine.testdata;

import ru.tinkoff.kora.cache.caffeine.AbstractCaffeineCache;
import ru.tinkoff.kora.cache.caffeine.CaffeineCacheConfig;
import ru.tinkoff.kora.cache.caffeine.CaffeineCacheFactory;
import ru.tinkoff.kora.cache.telemetry.CacheTelemetryFactory;

public final class DummyCache extends AbstractCaffeineCache<String, String> {

    public DummyCache(CaffeineCacheConfig config, CaffeineCacheFactory factory, CacheTelemetryFactory telemetryFactory) {
        super("dummy", config, factory, telemetryFactory);
    }
}
