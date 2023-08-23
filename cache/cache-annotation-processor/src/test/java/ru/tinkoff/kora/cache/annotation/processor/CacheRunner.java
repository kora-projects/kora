package ru.tinkoff.kora.cache.annotation.processor;

import reactor.core.publisher.Mono;
import ru.tinkoff.kora.cache.caffeine.CaffeineCacheConfig;
import ru.tinkoff.kora.cache.redis.RedisCacheConfig;
import ru.tinkoff.kora.cache.redis.client.ReactiveRedisClient;
import ru.tinkoff.kora.cache.redis.client.SyncRedisClient;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

final class CacheRunner {

    private CacheRunner() { }

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
        };
    }

    public static RedisCacheConfig getRedisConfig() {
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

    public static SyncRedisClient syncRedisClient(final Map<ByteBuffer, ByteBuffer> cache) {
        return new SyncRedisClient() {
            @Override
            public byte[] get(byte[] key) {
                var r = cache.get(ByteBuffer.wrap(key));
                return (r == null)
                    ? null
                    : r.array();
            }

            @Nonnull
            @Override
            public Map<byte[], byte[]> get(byte[][] keys) {
                final Map<byte[], byte[]> result = new HashMap<>();
                for (byte[] key : keys) {
                    Optional.ofNullable(cache.get(ByteBuffer.wrap(key))).ifPresent(r -> result.put(key, r.array()));
                }
                return result;
            }

            @Override
            public byte[] getExpire(byte[] key, long expireAfterMillis) {
                return get(key);
            }

            @Nonnull
            @Override
            public Map<byte[], byte[]> getExpire(byte[][] keys, long expireAfterMillis) {
                return get(keys);
            }

            @Override
            public void set(byte[] key, byte[] value) {
                cache.put(ByteBuffer.wrap(key), ByteBuffer.wrap(value));
            }

            @Override
            public void setExpire(byte[] key, byte[] value, long expireAfterMillis) {
                set(key, value);
            }

            @Override
            public long del(byte[] key) {
                return cache.remove(ByteBuffer.wrap(key)) == null ? 0 : 1 ;
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

    public static ReactiveRedisClient reactiveRedisClient(final Map<ByteBuffer, ByteBuffer> cache) {
        var syncRedisClient = syncRedisClient(cache);
        return new ReactiveRedisClient() {
            @Nonnull
            @Override
            public Mono<byte[]> get(byte[] key) {
                return Mono.justOrEmpty(syncRedisClient.get(key));
            }

            @Nonnull
            @Override
            public Mono<Map<byte[], byte[]>> get(byte[][] keys) {
                return Mono.justOrEmpty(syncRedisClient.get(keys));
            }

            @Nonnull
            @Override
            public Mono<byte[]> getExpire(byte[] key, long expireAfterMillis) {
                return get(key);
            }

            @Nonnull
            @Override
            public Mono<Map<byte[], byte[]>> getExpire(byte[][] keys, long expireAfterMillis) {
                return get(keys);
            }

            @Nonnull
            @Override
            public Mono<Boolean> set(byte[] key, byte[] value) {
                syncRedisClient.set(key, value);
                return Mono.just(true);
            }

            @Nonnull
            @Override
            public Mono<Boolean> setExpire(byte[] key, byte[] value, long expireAfterMillis) {
                return set(key, value);
            }

            @Nonnull
            @Override
            public Mono<Long> del(byte[] key) {
                return Mono.justOrEmpty(syncRedisClient.del(key));
            }

            @Nonnull
            @Override
            public Mono<Long> del(byte[][] keys) {
                return Mono.justOrEmpty(syncRedisClient.del(keys));
            }

            @Nonnull
            @Override
            public Mono<Boolean> flushAll() {
                syncRedisClient.flushAll();
                return Mono.just(true);
            }
        };
    }
}
