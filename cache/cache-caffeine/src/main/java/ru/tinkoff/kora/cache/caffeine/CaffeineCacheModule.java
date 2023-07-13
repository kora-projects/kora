package ru.tinkoff.kora.cache.caffeine;

import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.cache.CacheManager;
import ru.tinkoff.kora.cache.telemetry.CacheMetrics;
import ru.tinkoff.kora.cache.telemetry.CacheTelemetry;
import ru.tinkoff.kora.cache.telemetry.DefaultCacheTelemetry;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;

import javax.annotation.Nullable;

public interface CaffeineCacheModule {

    @Tag(CaffeineCacheManager.class)
    @DefaultComponent
    default CacheTelemetry defaultCacheTelemetry(@Nullable CacheMetrics metrics) {
        return new DefaultCacheTelemetry(metrics, null);
    }

    default CaffeineCacheConfig caffeineCacheConfig(Config config, ConfigValueExtractor<CaffeineCacheConfig> extractor) {
        return extractor.extract(config.get("cache"));
    }

    default CaffeineCacheFactory caffeineCacheFactory() {
        return new CaffeineCacheFactory();
    }

    @Tag(CaffeineCacheManager.class)
    default <K, V> CacheManager<K, V> taggedCaffeineCacheManager(CaffeineCacheFactory factory,
                                                                 CaffeineCacheConfig config,
                                                                 @Tag(CaffeineCacheManager.class) CacheTelemetry telemetry,
                                                                 TypeRef<K> keyRef,
                                                                 TypeRef<V> valueRef) {
        return new CaffeineCacheManager<>(factory, config, telemetry);
    }
}
