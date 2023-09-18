package ru.tinkoff.kora.cache.redis;

import jakarta.annotation.Nullable;
import org.junit.jupiter.api.Assertions;
import ru.tinkoff.kora.cache.redis.client.LettuceClientConfig;
import ru.tinkoff.kora.cache.redis.testdata.DummyCache;
import ru.tinkoff.kora.test.redis.RedisParams;

import java.time.Duration;

abstract class CacheRunner extends Assertions implements RedisCacheModule {

    public static RedisCacheConfig getConfig() {
        return new RedisCacheConfig() {
            @Nullable
            @Override
            public Duration expireAfterWrite() {
                return null;
            }

            @Nullable
            @Override
            public Duration expireAfterAccess() {
                return null;
            }
        };
    }

    protected DummyCache createCache(RedisParams redisParams) throws Exception {
        var lettuceClientFactory = lettuceClientFactory();
        var lettuceClientConfig = new LettuceClientConfig(redisParams.uri().toString(), null, null, null, null, null, null);
        var lettuceCommander = lettuceCommander(lettuceClientFactory, lettuceClientConfig);
        lettuceCommander.init();

        var syncRedisClient = lettuceCacheRedisClient(lettuceCommander);
        var reactiveRedisClient = lettuceReactiveCacheRedisClient(lettuceCommander);
        return new DummyCache(getConfig(), syncRedisClient, reactiveRedisClient, redisCacheTelemetry(null, null),
            stringRedisKeyMapper(), stringRedisValueMapper());
    }
}
