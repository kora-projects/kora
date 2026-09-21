package io.koraframework.cache.redis;

import io.koraframework.application.graph.Lifecycle;
import io.koraframework.cache.redis.lettuce.LettuceRedisCacheModule;
import io.koraframework.cache.redis.telemetry.$RedisCacheTelemetryConfig_ConfigValueMapper;
import io.koraframework.cache.redis.telemetry.$RedisCacheTelemetryConfig_RedisCacheLoggingConfig_ConfigValueMapper;
import io.koraframework.cache.redis.telemetry.$RedisCacheTelemetryConfig_RedisCacheMetricsConfig_ConfigValueMapper;
import io.koraframework.cache.redis.telemetry.$RedisCacheTelemetryConfig_RedisCacheTracingConfig_ConfigValueMapper;
import io.koraframework.cache.redis.telemetry.RedisCacheTelemetryConfig;
import io.koraframework.cache.redis.testdata.DummyCache;
import io.koraframework.redis.lettuce.$LettuceConfig_SslConfig_ConfigValueMapper;
import io.koraframework.redis.lettuce.LettuceConfig;
import io.koraframework.redis.lettuce.telemetry.$LettuceTelemetryConfig_ConfigValueMapper;
import io.koraframework.redis.lettuce.telemetry.$LettuceTelemetryConfig_LettuceLoggingConfig_ConfigValueMapper;
import io.koraframework.redis.lettuce.telemetry.$LettuceTelemetryConfig_LettuceMetricsConfig_ConfigValueMapper;
import io.koraframework.redis.lettuce.telemetry.LettuceTelemetryConfig;
import io.koraframework.test.redis.RedisParams;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Assertions;

import java.time.Duration;

public abstract class CacheRunner extends Assertions implements LettuceRedisCacheModule {

    public static RedisCacheConfig getConfig(String prefix,
                                             @Nullable Duration expireWrite,
                                             @Nullable Duration expireRead) {
        return getConfig(prefix, expireWrite, expireRead, true);
    }

    public static RedisCacheConfig getConfig(String prefix,
                                             @Nullable Duration expireWrite,
                                             @Nullable Duration expireRead,
                                             boolean enabled) {
        return new RedisCacheConfig() {

            @Override
            public boolean enabled() {
                return enabled;
            }

            @Override
            public String keyPrefix() {
                return prefix;
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

    private RedisCacheClient createLettuce(RedisParams redisParams, int dbIndex) throws Exception {
        var lettuceClientFactory = lettuceFactory().lettuceFactory(null, null, null, null, null, null);
        var lettuceConfig = new LettuceConfig() {
            @Override
            public String uri() {
                return redisParams.uri().toString();
            }

            @Override
            public Integer database() {
                return dbIndex;
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

    private DummyCache createDummyCache(RedisParams redisParams, String prefix, int dbIndex, Duration expireWrite, Duration expireRead, boolean enabled) throws Exception {
        var lettuceClient = createLettuce(redisParams, dbIndex);
        return new DummyCache(getConfig(prefix, expireWrite, expireRead, enabled), lettuceClient, defaultRedisCacheTelemetryFactory(null, null, null, null),
            stringRedisCacheKeyMapper(), stringRedisCacheValueMapper());
    }

    protected DummyCache createCache(RedisParams redisParams, String prefix, int dbIndex) throws Exception {
        return createDummyCache(redisParams, prefix, dbIndex, null, null, true);
    }

    protected DummyCache createCacheExpireWrite(RedisParams redisParams, String prefix, int dbIndex, Duration expireWrite) throws Exception {
        return createDummyCache(redisParams, prefix, dbIndex, expireWrite, null, true);
    }

    protected DummyCache createCacheExpireRead(RedisParams redisParams, String prefix, int dbIndex, Duration expireRead) throws Exception {
        return createDummyCache(redisParams, prefix, dbIndex, null, expireRead, true);
    }

    protected DummyCache createCacheDisabled(RedisParams redisParams, String prefix, int dbIndex) throws Exception {
        return createDummyCache(redisParams, prefix, dbIndex, null, null, false);
    }
}
