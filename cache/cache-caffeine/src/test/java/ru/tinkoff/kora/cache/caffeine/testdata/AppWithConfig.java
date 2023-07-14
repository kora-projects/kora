package ru.tinkoff.kora.cache.caffeine.testdata;

import ru.tinkoff.kora.cache.caffeine.CaffeineCacheModule;
import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.DefaultConfigExtractorsModule;
import ru.tinkoff.kora.config.common.factory.MapConfigFactory;

import java.util.Map;

@KoraApp
public interface AppWithConfig extends DefaultConfigExtractorsModule, CaffeineCacheModule {

    default Config config() {
        return MapConfigFactory.fromMap(Map.of(
            "cache", Map.of(
                "caffeine", Map.of(
                    "sync_cache", Map.of("maximumSize", 10),
                    "mono_cache", Map.of("maximumSize", 10)
                )
            )
        ));
    }

    @Root
    default CacheableMockLifecycle object(CacheableTargetSync cacheableTargetSync, CacheableTargetMono cacheableTargetMono) {
        return new CacheableMockLifecycle(cacheableTargetMono, cacheableTargetSync);
    }
}
