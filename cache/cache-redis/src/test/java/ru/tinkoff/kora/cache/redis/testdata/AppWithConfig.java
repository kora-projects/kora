package ru.tinkoff.kora.cache.redis.testdata;

import ru.tinkoff.kora.cache.redis.CacheRunner;
import ru.tinkoff.kora.cache.redis.DefaultRedisCacheModule;
import ru.tinkoff.kora.cache.redis.client.SyncRedisClient;
import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.DefaultConfigExtractorsModule;
import ru.tinkoff.kora.config.common.factory.MapConfigFactory;

import java.util.Map;

@KoraApp
public interface AppWithConfig extends DefaultConfigExtractorsModule, DefaultRedisCacheModule {

    default Config config() {
        return MapConfigFactory.fromMap(Map.of(
            "lettuce", Map.of(
                "uri", CacheRunner.redisUri.toString(),
                "timeout", "15s"
            )
        ));
    }

    @Root
    default CacheableMockLifecycle object(CacheableTargetSync cacheableTargetSync, CacheableTargetMono cacheableTargetMono, SyncRedisClient cacheClient) {
        return new CacheableMockLifecycle(cacheableTargetMono, cacheableTargetSync, cacheClient);
    }
}
