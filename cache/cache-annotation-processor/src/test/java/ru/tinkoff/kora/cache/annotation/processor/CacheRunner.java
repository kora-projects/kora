package ru.tinkoff.kora.cache.annotation.processor;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.cache.caffeine.CaffeineCacheConfig;
import ru.tinkoff.kora.cache.redis.RedisCacheAsyncClient;
import ru.tinkoff.kora.cache.redis.RedisCacheClient;
import ru.tinkoff.kora.cache.redis.RedisCacheConfig;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

final class CacheRunner {

    private CacheRunner() {}

    public static CaffeineCacheConfig getCaffeineConfig() {
        return new CaffeineCacheConfig() {
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

            @Nullable
            @Override
            public Integer initialSize() {
                return null;
            }

            @Override
            public TelemetryConfig telemetry() {
                return null;
            }
        };
    }

    public static RedisCacheConfig getRedisConfig() {
        return new RedisCacheConfig() {

            @Override
            public String keyPrefix() {
                return "pref";
            }

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

            @Override
            public TelemetryConfig telemetry() {
                return null;
            }
        };
    }

    public static RedisCacheAsyncClient lettuceAsyncClient(final Map<ByteBuffer, ByteBuffer> cache) {
        return new RedisCacheAsyncClient() {
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
            public CompletionStage<Void> set(byte[] key, byte[] value) {
                cache.put(ByteBuffer.wrap(key), ByteBuffer.wrap(value));
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<Void> mset(Map<byte[], byte[]> keyAndValue) {
                keyAndValue.forEach((k, v) -> cache.put(ByteBuffer.wrap(k), ByteBuffer.wrap(v)));
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public CompletionStage<Void> psetex(Map<byte[], byte[]> keyAndValue, long expireAfterMillis) {
                return mset(keyAndValue);
            }

            @Override
            public CompletionStage<Void> psetex(byte[] key, byte[] value, long expireAfterMillis) {
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
            public CompletionStage<Void> flushAll() {
                cache.clear();
                return CompletableFuture.completedFuture(null);
            }
        };
    }

    public static RedisCacheClient lettuceSyncClient(final Map<ByteBuffer, ByteBuffer> cache) {
        return new RedisCacheClient() {
            @Override
            public byte[] get(byte[] key) {
                var r = cache.get(ByteBuffer.wrap(key));
                return (r == null)
                    ? null
                    : r.array();
            }

            @Nonnull
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

            @Nonnull
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
                long counter = 0;
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
