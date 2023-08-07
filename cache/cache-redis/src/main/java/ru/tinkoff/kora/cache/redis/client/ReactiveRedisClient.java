package ru.tinkoff.kora.cache.redis.client;

import reactor.core.publisher.Mono;

import java.util.Map;

public interface ReactiveRedisClient {

    Mono<byte[]> get(byte[] key);

    Mono<Map<byte[], byte[]>> get(byte[][] keys);

    Mono<byte[]> getExpire(byte[] key, long expireAfterMillis);

    Mono<Map<byte[], byte[]>> getExpire(byte[][] key, long expireAfterMillis);

    Mono<Boolean> set(byte[] key, byte[] value);

    Mono<Boolean> setExpire(byte[] key, byte[] value, long expireAfterMillis);

    Mono<Long> del(byte[] key);

    Mono<Long> del(byte[][] keys);

    Mono<Boolean> flushAll();
}
