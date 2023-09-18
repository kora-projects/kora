package ru.tinkoff.kora.cache.redis.client;

import reactor.core.publisher.Mono;

import jakarta.annotation.Nonnull;
import java.util.Map;

public interface ReactiveRedisClient {

    @Nonnull
    Mono<byte[]> get(byte[] key);

    @Nonnull
    Mono<Map<byte[], byte[]>> get(byte[][] keys);

    @Nonnull
    Mono<byte[]> getExpire(byte[] key, long expireAfterMillis);

    @Nonnull
    Mono<Map<byte[], byte[]>> getExpire(byte[][] keys, long expireAfterMillis);

    @Nonnull
    Mono<Boolean> set(byte[] key, byte[] value);

    @Nonnull
    Mono<Boolean> setExpire(byte[] key, byte[] value, long expireAfterMillis);

    @Nonnull
    Mono<Long> del(byte[] key);

    @Nonnull
    Mono<Long> del(byte[][] keys);

    @Nonnull
    Mono<Boolean> flushAll();
}
