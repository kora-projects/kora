package ru.tinkoff.kora.cache.redis;

import jakarta.annotation.Nullable;
import org.junit.jupiter.api.Assertions;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.cache.redis.lettuce.$LettuceClientConfig_SslConfig_ConfigValueExtractor;
import ru.tinkoff.kora.cache.redis.lettuce.LettuceClientConfig;
import ru.tinkoff.kora.cache.redis.testdata.DummyCache;
import ru.tinkoff.kora.telemetry.common.*;
import ru.tinkoff.kora.test.redis.RedisParams;

import java.time.Duration;
import java.util.Map;

abstract class CacheRunner extends Assertions implements RedisCacheModule {

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
        };
    }

    private RedisCacheClient createLettuce(RedisParams redisParams) throws Exception {
        var lettuceClientFactory = lettuceClientFactory();
        var lettuceClientConfig = new LettuceClientConfig() {
            @Override
            public String uri() {
                return redisParams.uri().toString();
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
            public SslConfig ssl() {
                return new $LettuceClientConfig_SslConfig_ConfigValueExtractor.SslConfig_Defaults();
            }

            @Override
            public TelemetryConfig telemetry() {
                return new $TelemetryConfig_ConfigValueExtractor.TelemetryConfig_Impl(new $TelemetryConfig_LogConfig_ConfigValueExtractor.LogConfig_Impl(false),
                    new $TelemetryConfig_TracingConfig_ConfigValueExtractor.TracingConfig_Impl(false, Map.of()),
                    new $TelemetryConfig_MetricsConfig_ConfigValueExtractor.MetricsConfig_Impl(false, new Duration[0], Map.of()));
            }
        };

        var lettuceClient = lettuceRedisClient(lettuceClientFactory, lettuceClientConfig, null, null, null);
        if (lettuceClient instanceof Lifecycle lc) {
            lc.init();
        }
        return lettuceClient;
    }

    private DummyCache createDummyCache(RedisParams redisParams, Duration expireWrite, Duration expireRead) throws Exception {
        var lettuceClient = createLettuce(redisParams);
        return new DummyCache(getConfig(expireWrite, expireRead), lettuceClient, redisCacheTelemetry(null, null),
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
