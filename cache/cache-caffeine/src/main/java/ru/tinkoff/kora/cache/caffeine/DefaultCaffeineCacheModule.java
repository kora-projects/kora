package ru.tinkoff.kora.cache.caffeine;

import ru.tinkoff.kora.application.graph.TypeRef;
import ru.tinkoff.kora.cache.CacheManager;
import ru.tinkoff.kora.cache.telemetry.CacheTelemetry;
import ru.tinkoff.kora.common.Tag;

public interface DefaultCaffeineCacheModule extends CaffeineCacheModule {

    default <K, V> CacheManager<K, V> defaultCaffeineCacheManager(CaffeineCacheFactory factory,
                                                                  CaffeineCacheConfig config,
                                                                  @Tag(CaffeineCacheManager.class) CacheTelemetry telemetry,
                                                                  TypeRef<K> keyRef,
                                                                  TypeRef<V> valueRef) {
        return taggedCaffeineCacheManager(factory, config, telemetry, keyRef, valueRef);
    }
}
