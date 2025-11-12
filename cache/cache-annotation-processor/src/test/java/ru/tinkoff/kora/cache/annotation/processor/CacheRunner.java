package ru.tinkoff.kora.cache.annotation.processor;

import jakarta.annotation.Nonnull;
import org.mockito.Mockito;
import ru.tinkoff.kora.cache.caffeine.$CaffeineCacheConfig_CaffeineTelemetryConfig_CaffeineLoggingConfig_ConfigValueExtractor;
import ru.tinkoff.kora.cache.caffeine.$CaffeineCacheConfig_CaffeineTelemetryConfig_CaffeineMetricsConfig_ConfigValueExtractor;
import ru.tinkoff.kora.cache.caffeine.CaffeineCacheConfig;
import ru.tinkoff.kora.cache.redis.RedisCacheClient;
import ru.tinkoff.kora.cache.redis.RedisCacheConfig;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

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
        return config;
    }

    public static RedisCacheClient lettuceClient(final Map<ByteBuffer, ByteBuffer> cache) {
        return new RedisCacheClient() {
            @Override
            public CompletionStage<List<byte[]>> scan(byte[] prefix) {
                List<byte[]> keys = new ArrayList<>();
                for (ByteBuffer buffer : cache.keySet()) {
                    if (Arrays.equals(Arrays.copyOf(buffer.array(), prefix.length), prefix)) {
                        keys.add(buffer.array());
                    }
                }
                return CompletableFuture.completedFuture(keys);
            }

            @Override
            public CompletionStage<byte[]> get(byte[] key) {
                var r = cache.get(ByteBuffer.wrap(key));
                return (r == null)
                    ? CompletableFuture.completedFuture(null)
                    : CompletableFuture.completedFuture(r.array());
            }

            @Nonnull
            @Override
            public CompletionStage<Map<byte[], byte[]>> mget(byte[][] keys) {
                final Map<byte[], byte[]> result = new HashMap<>();
                for (byte[] key : keys) {
                    Optional.ofNullable(cache.get(ByteBuffer.wrap(key))).ifPresent(r -> result.put(key, r.array()));
                }
                return CompletableFuture.completedFuture(result);
            }

            @Override
            public CompletionStage<byte[]> getex(byte[] key, long expireAfterMillis) {
                return get(key);
            }

            @Nonnull
            @Override
            public CompletionStage<Map<byte[], byte[]>> getex(byte[][] keys, long expireAfterMillis) {
                return mget(keys);
            }

            @Override
            public CompletionStage<Boolean> set(byte[] key, byte[] value) {
                cache.put(ByteBuffer.wrap(key), ByteBuffer.wrap(value));
                return CompletableFuture.completedFuture(true);
            }

            @Override
            public CompletionStage<Boolean> mset(Map<byte[], byte[]> keyAndValue) {
                keyAndValue.forEach((k, v) -> cache.put(ByteBuffer.wrap(k), ByteBuffer.wrap(v)));
                return CompletableFuture.completedFuture(true);
            }

            @Override
            public CompletionStage<Boolean> psetex(Map<byte[], byte[]> keyAndValue, long expireAfterMillis) {
                return mset(keyAndValue);
            }

            @Override
            public CompletionStage<Boolean> psetex(byte[] key, byte[] value, long expireAfterMillis) {
                return set(key, value);
            }

            @Override
            public CompletionStage<Long> del(byte[] key) {
                return CompletableFuture.completedFuture(cache.remove(ByteBuffer.wrap(key)) == null ? 0L : 1L);
            }

            @Override
            public CompletionStage<Long> del(byte[][] keys) {
                int counter = 0;
                for (byte[] key : keys) {
                    counter += del(key).toCompletableFuture().join();
                }
                return CompletableFuture.completedFuture((long) counter);
            }

            @Override
            public CompletionStage<Boolean> flushAll() {
                cache.clear();
                return CompletableFuture.completedFuture(true);
            }
        };
    }
}
