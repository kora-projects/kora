package io.koraframework.cache.redis;

import io.koraframework.application.graph.Lifecycle;
import io.koraframework.cache.redis.telemetry.*;
import io.koraframework.cache.redis.testdata.DummyCache;
import io.koraframework.redis.lettuce.$LettuceConfig_SslConfig_ConfigValueMapper;
import io.koraframework.redis.lettuce.LettuceConfig;
import io.koraframework.redis.lettuce.telemetry.*;
import io.koraframework.redis.lettuce.telemetry.$LettuceTelemetryConfig_ConfigValueMapper;
import io.koraframework.redis.lettuce.telemetry.$LettuceTelemetryConfig_LettuceMetricsConfig_ConfigValueMapper;
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
                return new $RedisCacheTelemetryConfig_ConfigValueMapper.RedisCacheTelemetryConfig_Impl(
                    new $RedisCacheTelemetryConfig_RedisCacheLoggingConfig_ConfigValueMapper.RedisCacheLoggingConfig_Defaults(),
                    new $RedisCacheTelemetryConfig_RedisCacheTracingConfig_ConfigValueMapper.RedisCacheTracingConfig_Defaults(),
                    new $RedisCacheTelemetryConfig_RedisCacheMetricsConfig_ConfigValueMapper.RedisCacheMetricsConfig_Defaults()
                );
            }
        };
    }

    private RedisCacheClient createLettuce(RedisParams redisParams) throws Exception {
        var lettuceClientFactory = lettuceFactory().lettuceFactory(null, null, null, null, null, null);
        var lettuceConfig = new LettuceConfig() {
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
            public LettuceConfig.SslConfig ssl() {
                return new $LettuceConfig_SslConfig_ConfigValueMapper.SslConfig_Defaults();
            }

            @Override
            public LettuceTelemetryConfig telemetry() {
                return new $LettuceTelemetryConfig_ConfigValueMapper.LettuceTelemetryConfig_Impl(
                    new $LettuceTelemetryConfig_LettuceLoggingConfig_ConfigValueMapper.LettuceLoggingConfig_Defaults(),
                    new $LettuceTelemetryConfig_LettuceMetricsConfig_ConfigValueMapper.LettuceMetricsConfig_Defaults()
                );
            }
        };

        var lettuceClient = lettuceRedisCacheClient(lettuceClientFactory.build(lettuceConfig), lettuceClientFactory, lettuceConfig);
        if (lettuceClient instanceof Lifecycle lc) {
            lc.init();
        }
        return lettuceClient;
    }

    private DummyCache createDummyCache(RedisParams redisParams, Duration expireWrite, Duration expireRead) throws Exception {
        var lettuceClient = createLettuce(redisParams);
        return new DummyCache(getConfig(expireWrite, expireRead), lettuceClient, defaultRedisCacheTelemetryFactory(null, null, null, null),
            stringRedisCacheKeyMapper(), stringRedisCacheValueMapper());
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
