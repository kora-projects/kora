package ru.tinkoff.kora.cache.redis.testdata;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import ru.tinkoff.kora.annotation.processor.common.MockLifecycle;
import ru.tinkoff.kora.cache.redis.CacheRunner;
import ru.tinkoff.kora.cache.redis.DefaultRedisCacheModule;
import ru.tinkoff.kora.cache.redis.client.SyncRedisClient;
import ru.tinkoff.kora.common.KoraApp;
import ru.tinkoff.kora.config.common.DefaultConfigExtractorsModule;

@KoraApp
public interface AppWithConfig extends DefaultConfigExtractorsModule, DefaultRedisCacheModule {

    default Config config() {
        return ConfigFactory.parseString(String.format(
            """
                lettuce {
                  uri = "%s"
                  timeout = 15s
                }
                """, CacheRunner.redisUri)).resolve();
    }

    default MockLifecycle object(CacheableTargetSync cacheableTargetSync, CacheableTargetMono cacheableTargetMono, SyncRedisClient cacheClient) {
        return new CacheableMockLifecycle(cacheableTargetMono, cacheableTargetSync, cacheClient);
    }
}
