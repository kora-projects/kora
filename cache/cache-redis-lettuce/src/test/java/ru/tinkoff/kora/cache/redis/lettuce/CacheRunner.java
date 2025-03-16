package ru.tinkoff.kora.cache.redis.lettuce;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import jakarta.annotation.Nullable;
import org.junit.jupiter.api.Assertions;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.cache.redis.RedisCacheAsyncClient;
import ru.tinkoff.kora.cache.redis.RedisCacheClient;
import ru.tinkoff.kora.cache.redis.RedisCacheConfig;
import ru.tinkoff.kora.cache.redis.RedisCacheMapperModule;
import ru.tinkoff.kora.cache.redis.lettuce.testdata.DummyCache;
import ru.tinkoff.kora.cache.telemetry.CacheTelemetry;
import ru.tinkoff.kora.redis.lettuce.LettuceConfig;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;
import ru.tinkoff.kora.test.redis.RedisParams;

import java.time.Duration;
import java.util.List;

abstract class CacheRunner extends Assertions implements RedisCacheMapperModule, LettuceCacheModule {

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

    private RedisCacheAsyncClient createLettuce(RedisParams redisParams) throws Exception {
        var lettuceClientConfig = new LettuceConfig() {
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

            @Override
            public PoolConfig pool() {
                return null;
            }
        };

        var lettuceClient = lettuceClient(lettuceClientConfig);
        if (lettuceClient instanceof Lifecycle lc) {
            lc.init();
        }

        if (!(lettuceClient instanceof RedisClient rc)) {
            throw new IllegalStateException();
        }

        Wrapped<StatefulConnection<byte[], byte[]>> statefulConnectionWrapped = lettuceStatefulConnection(lettuceClient, ByteArrayCodec.INSTANCE);
        if(statefulConnectionWrapped instanceof Lifecycle l) {
            l.init();
        }
        var commands = lettuceRedisClusterAsyncCommands(statefulConnectionWrapped.value());
        LettuceSingleCacheAsyncClient lettuceSingleCacheAsyncClient = new LettuceSingleCacheAsyncClient(rc, commands, RedisURI.create(redisParams.uri()));
        lettuceSingleCacheAsyncClient.init();
        return lettuceSingleCacheAsyncClient;
    }

    private RedisCacheClient createSyncLettuce(RedisCacheAsyncClient asyncClient) {
        return new LettuceCacheSyncClient(asyncClient);
    }

    private DummyCache createDummyCache(RedisParams redisParams, Duration expireWrite, Duration expireRead) throws Exception {
        var lettuceClient = createLettuce(redisParams);
        var lettuceSyncClient = createSyncLettuce(lettuceClient);
        return new DummyCache(getConfig(expireWrite, expireRead), lettuceSyncClient, lettuceClient,
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
