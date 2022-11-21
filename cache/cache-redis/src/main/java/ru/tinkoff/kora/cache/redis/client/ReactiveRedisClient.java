package ru.tinkoff.kora.cache.redis.client;

import reactor.core.publisher.Mono;

public interface ReactiveRedisClient {

    Mono<byte[]> get(byte[] key);

    Mono<byte[]> getExpire(byte[] key, long expireAfterMillis);

    Mono<Void> set(byte[] key, byte[] value);

    Mono<Void> setExpire(byte[] key, byte[] value, long expireAfterMillis);

    Mono<Void> del(byte[] key);

    Mono<Void> flushAll();
}
