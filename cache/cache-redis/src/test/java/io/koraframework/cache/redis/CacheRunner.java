package io.koraframework.cache.redis;

import io.koraframework.application.graph.Lifecycle;
import io.koraframework.cache.redis.lettuce.$LettuceClientConfig_LettuceTelemetryConfig_ConfigValueExtractor;
import io.koraframework.cache.redis.lettuce.$LettuceClientConfig_LettuceTelemetryConfig_LettuceMetricsConfig_ConfigValueExtractor;
import io.koraframework.cache.redis.lettuce.$LettuceClientConfig_SslConfig_ConfigValueExtractor;
import io.koraframework.cache.redis.lettuce.LettuceClientConfig;
import io.koraframework.cache.redis.testdata.DummyCache;
import io.koraframework.test.redis.RedisParams;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assertions;

import java.time.Duration;

public abstract class CacheRunner extends Assertions implements RedisCacheModule {

    public static final String PREFIX = "pref";

    public static RedisCacheConfig getConfig(@Nullable Duration expireWrite,
                                             @Nullable Duration expireRead) {
        return new RedisCacheConfig() {

            @Override
            public String keyPrefix() {
                return PREFIX;
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
            public RedisCacheTelemetryConfig telemetry() {
                return new $RedisCacheConfig_RedisCacheTelemetryConfig_ConfigValueExtractor.RedisCacheTelemetryConfig_Impl(
                    new $RedisCacheConfig_RedisCacheTelemetryConfig_RedisCacheLoggingConfig_ConfigValueExtractor.RedisCacheLoggingConfig_Defaults(),
                    new $RedisCacheConfig_RedisCacheTelemetryConfig_RedisCacheTracingConfig_ConfigValueExtractor.RedisCacheTracingConfig_Defaults(),
                    new $RedisCacheConfig_RedisCacheTelemetryConfig_RedisCacheMetricsConfig_ConfigValueExtractor.RedisCacheMetricsConfig_Defaults()
                );
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
            public LettuceTelemetryConfig telemetry() {
                return new $LettuceClientConfig_LettuceTelemetryConfig_ConfigValueExtractor.LettuceTelemetryConfig_Impl(
                    new $LettuceClientConfig_LettuceTelemetryConfig_LettuceMetricsConfig_ConfigValueExtractor.LettuceMetricsConfig_Defaults()
                );
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
        return new DummyCache(getConfig(expireWrite, expireRead), lettuceClient, redisCacheTelemetryFactory(null, null),
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
