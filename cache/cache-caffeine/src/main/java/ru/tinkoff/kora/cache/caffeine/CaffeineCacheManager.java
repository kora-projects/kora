package ru.tinkoff.kora.cache.caffeine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.cache.CacheManager;
import ru.tinkoff.kora.cache.telemetry.CacheTelemetry;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class CaffeineCacheManager<K, V> implements CacheManager<K, V> {

    private static final Logger logger = LoggerFactory.getLogger(CaffeineCacheManager.class);

    private final CaffeineCacheFactory factory;
    private final CaffeineCacheConfig config;
    private final CacheTelemetry telemetry;

    private final Map<String, ru.tinkoff.kora.cache.Cache<K, V>> cacheMap = new ConcurrentHashMap<>();

    CaffeineCacheManager(CaffeineCacheFactory factory, CaffeineCacheConfig config, CacheTelemetry telemetry) {
        this.factory = factory;
        this.config = config;
        this.telemetry = telemetry;
    }

    @Nonnull
    @Override
    public ru.tinkoff.kora.cache.Cache<K, V> getCache(@Nonnull String name) {
        return cacheMap.computeIfAbsent(name, k -> {
            logger.trace("Build cache for name: {}", name);
            var namedConfig = config.getByName(name);
            return new CaffeineCache<>(name, factory.build(namedConfig), telemetry);
        });
    }
}
