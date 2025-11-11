package ru.tinkoff.kora.cache.caffeine.testdata;

import ru.tinkoff.kora.cache.caffeine.AbstractCaffeineCache;
import ru.tinkoff.kora.cache.caffeine.CaffeineCacheConfig;
import ru.tinkoff.kora.cache.caffeine.CaffeineCacheFactory;

public final class DummyCache extends AbstractCaffeineCache<String, String> {

    public DummyCache(CaffeineCacheConfig config, CaffeineCacheFactory factory) {
        super("dummy", config, factory);
    }
}
