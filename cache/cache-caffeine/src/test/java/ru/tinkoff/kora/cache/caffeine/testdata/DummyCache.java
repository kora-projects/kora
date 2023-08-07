package ru.tinkoff.kora.cache.caffeine.testdata;

import ru.tinkoff.kora.cache.caffeine.AbstractCaffeineCache;
import ru.tinkoff.kora.cache.caffeine.CaffeineCacheConfig;
import ru.tinkoff.kora.cache.caffeine.CaffeineCacheFactory;
import ru.tinkoff.kora.cache.caffeine.CaffeineCacheTelemetry;

public final class DummyCache extends AbstractCaffeineCache<String, String> {

    public DummyCache(CaffeineCacheConfig config, CaffeineCacheFactory factory, CaffeineCacheTelemetry telemetry) {
        super("dummy", config, factory, telemetry);
    }
}
