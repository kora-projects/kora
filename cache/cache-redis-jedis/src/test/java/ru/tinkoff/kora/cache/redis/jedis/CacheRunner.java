package ru.tinkoff.kora.cache.redis.jedis;

import jakarta.annotation.Nullable;
import org.junit.jupiter.api.Assertions;
import ru.tinkoff.kora.cache.redis.RedisCacheAsyncClient;
import ru.tinkoff.kora.cache.redis.RedisCacheClient;
import ru.tinkoff.kora.cache.redis.RedisCacheConfig;
import ru.tinkoff.kora.cache.redis.RedisCacheMapperModule;
import ru.tinkoff.kora.cache.redis.jedis.testdata.DummyCache;
import ru.tinkoff.kora.cache.telemetry.CacheTelemetry;
import ru.tinkoff.kora.redis.jedis.JedisConfig;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;
import ru.tinkoff.kora.test.redis.RedisParams;

import java.time.Duration;
import java.util.List;

abstract class CacheRunner extends Assertions implements RedisCacheMapperModule, JedisCacheModule {

    public static RedisCacheConfig getConfig(@Nullable Duration expireWrite,
                                             @Nullable Duration expireRead) {
        return new RedisCacheConfig() {

            @Override
            public String keyPrefix() {
                return "pref";
            }

            @Nullable
            @Override
            public Duration expireAfterWrite() {
                return expireWrite;
            }

            @Nullable
            @Override
            public Duration expireAfterAccess() {
                return expireRead;
            }

            @Override
            public TelemetryConfig telemetry() {
                return null;
            }
        };
    }

    private RedisCacheClient createJedis(RedisParams redisParams) throws Exception {
        var jedisConfig = new JedisConfig() {
            @Override
            public List<String> uri() {
                return List.of(redisParams.uri().toString());
            }

            @Override
            public Integer database() {
                return null;
            }

            @Override
            public String user() {
                return null;
            }

            @Override
            public String password() {
                return null;
            }
        };

        var jedis = jedisClient(jedisConfig);
        return new JedisCacheSyncClient(jedis);
    }

    private RedisCacheAsyncClient createAsyncJedis(RedisCacheClient cacheClient) throws Exception {
        return new JedisCacheAsyncClient(cacheClient);
    }

    private DummyCache createDummyCache(RedisParams redisParams, Duration expireWrite, Duration expireRead) throws Exception {
        var syncClient = createJedis(redisParams);
        var asyncClient = createAsyncJedis(syncClient);
        return new DummyCache(getConfig(expireWrite, expireRead), syncClient, asyncClient,
            (telemetryConfig, args) -> operationName -> new CacheTelemetry.CacheTelemetryContext() {
                @Override
                public void recordSuccess(Object valueFromCache) {}
                @Override
                public void recordFailure(Throwable throwable) {}
            },
            stringRedisKeyMapper(), stringRedisValueMapper());
    }

    protected DummyCache createCache(RedisParams redisParams) throws Exception {
        return createDummyCache(redisParams, null, null);
    }

    protected DummyCache createCacheExpireWrite(RedisParams redisParams, Duration expireWrite) throws Exception {
        return createDummyCache(redisParams, expireWrite, null);
    }

    protected DummyCache createCacheExpireRead(RedisParams redisParams, Duration expireRead) throws Exception {
        return createDummyCache(redisParams, null, expireRead);
    }
}
