package io.koraframework.cache.annotation.processor;

import io.koraframework.cache.caffeine.$CaffeineCacheConfig_CaffeineTelemetryConfig_CaffeineLoggingConfig_ConfigValueExtractor;
import io.koraframework.cache.caffeine.$CaffeineCacheConfig_CaffeineTelemetryConfig_CaffeineMetricsConfig_ConfigValueExtractor;
import io.koraframework.cache.caffeine.CaffeineCacheConfig;
import io.koraframework.cache.redis.*;
import io.koraframework.cache.redis.RedisCacheClient;
import io.koraframework.cache.redis.telemetry.$RedisCacheTelemetryConfig_ConfigValueExtractor;
import io.koraframework.cache.redis.telemetry.$RedisCacheTelemetryConfig_RedisCacheLoggingConfig_ConfigValueExtractor;
import io.koraframework.cache.redis.telemetry.$RedisCacheTelemetryConfig_RedisCacheMetricsConfig_ConfigValueExtractor;
import io.koraframework.cache.redis.telemetry.$RedisCacheTelemetryConfig_RedisCacheTracingConfig_ConfigValueExtractor;
import org.jspecify.annotations.NullMarked;
import org.mockito.Mockito;

import java.nio.ByteBuffer;
import java.util.*;

import static org.mockito.Mockito.when;

final class CacheRunner {

    private CacheRunner() {}

    public static CaffeineCacheConfig getCaffeineConfig() {
        var config = Mockito.mock(CaffeineCacheConfig.class);
        var telemetry = Mockito.mock(CaffeineCacheConfig.CaffeineTelemetryConfig.class);
        when(config.telemetry()).thenReturn(telemetry);
        when(config.maximumSize()).thenReturn(100_000L);
        when(config.expireAfterAccess()).thenReturn(null);
        when(config.expireAfterWrite()).thenReturn(null);
        when(telemetry.metrics()).thenReturn(new $CaffeineCacheConfig_CaffeineTelemetryConfig_CaffeineMetricsConfig_ConfigValueExtractor.CaffeineMetricsConfig_Defaults());
        when(telemetry.logging()).thenReturn(new $CaffeineCacheConfig_CaffeineTelemetryConfig_CaffeineLoggingConfig_ConfigValueExtractor.CaffeineLoggingConfig_Defaults());
        return config;
    }

    public static RedisCacheConfig getRedisConfig() {
        var config = Mockito.mock(RedisCacheConfig.class);
        when(config.keyPrefix()).thenReturn("pref");
        when(config.telemetry()).thenReturn(new $RedisCacheTelemetryConfig_ConfigValueExtractor.RedisCacheTelemetryConfig_Impl(
            new $RedisCacheTelemetryConfig_RedisCacheLoggingConfig_ConfigValueExtractor.RedisCacheLoggingConfig_Defaults(),
            new $RedisCacheTelemetryConfig_RedisCacheTracingConfig_ConfigValueExtractor.RedisCacheTracingConfig_Defaults(),
            new $RedisCacheTelemetryConfig_RedisCacheMetricsConfig_ConfigValueExtractor.RedisCacheMetricsConfig_Defaults()
        ));
        return config;
    }

    @NullMarked
    public static RedisCacheClient lettuceClient(final Map<ByteBuffer, ByteBuffer> cache) {
        return new RedisCacheClient() {
            @Override
            public List<byte[]> scan(byte[] prefix) {
                List<byte[]> keys = new ArrayList<>();
                for (ByteBuffer buffer : cache.keySet()) {
                    if (Arrays.equals(Arrays.copyOf(buffer.array(), prefix.length), prefix)) {
                        keys.add(buffer.array());
                    }
                }
                return keys;
            }

            @Override
            public byte[] get(byte[] key) {
                var r = cache.get(ByteBuffer.wrap(key));
                return (r == null)
                    ? null
                    : r.array();
            }

            @Override
            public Map<byte[], byte[]> mget(byte[][] keys) {
                final Map<byte[], byte[]> result = new HashMap<>();
                for (byte[] key : keys) {
                    Optional.ofNullable(cache.get(ByteBuffer.wrap(key))).ifPresent(r -> result.put(key, r.array()));
                }
                return result;
            }

            @Override
            public byte[] getex(byte[] key, long expireAfterMillis) {
                return get(key);
            }

            @Override
            public Map<byte[], byte[]> getex(byte[][] keys, long expireAfterMillis) {
                return mget(keys);
            }

            @Override
            public void set(byte[] key, byte[] value) {
                cache.put(ByteBuffer.wrap(key), ByteBuffer.wrap(value));
            }

            @Override
            public void mset(Map<byte[], byte[]> keyAndValue) {
                keyAndValue.forEach((k, v) -> cache.put(ByteBuffer.wrap(k), ByteBuffer.wrap(v)));
            }

            @Override
            public void psetex(Map<byte[], byte[]> keyAndValue, long expireAfterMillis) {
                mset(keyAndValue);
            }

            @Override
            public void psetex(byte[] key, byte[] value, long expireAfterMillis) {
                set(key, value);
            }

            @Override
            public long del(byte[] key) {
                return cache.remove(ByteBuffer.wrap(key)) == null ? 0L : 1L;
            }

            @Override
            public long del(byte[][] keys) {
                int counter = 0;
                for (byte[] key : keys) {
                    counter += del(key);
                }
                return counter;
            }

            @Override
            public void flushAll() {
                cache.clear();
            }
        };
    }
}
