package ru.tinkoff.kora.cache.redis;

import jakarta.annotation.Nonnull;

import java.util.Map;
import java.util.concurrent.CompletionStage;

/**
 * This module is no longer maintained, it was replaced with new one.
 * <p>
 * Use dependency - ru.tinkoff.kora:cache-redis-lettuce AND RedisCacheAsyncClient
 * <p>
 * Check documentation for more information
 */
@Deprecated
public interface RedisCacheClient {

    @Nonnull
    CompletionStage<byte[]> get(byte[] key);

    @Nonnull
    CompletionStage<Map<byte[], byte[]>> mget(byte[][] keys);

    @Nonnull
    CompletionStage<byte[]> getex(byte[] key, long expireAfterMillis);

    @Nonnull
    CompletionStage<Map<byte[], byte[]>> getex(byte[][] keys, long expireAfterMillis);

    @Nonnull
    CompletionStage<Boolean> set(byte[] key, byte[] value);

    @Nonnull
    CompletionStage<Boolean> mset(@Nonnull Map<byte[], byte[]> keyAndValue);

    @Nonnull
    CompletionStage<Boolean> psetex(byte[] key, byte[] value, long expireAfterMillis);

    @Nonnull
    CompletionStage<Boolean> psetex(@Nonnull Map<byte[], byte[]> keyAndValue, long expireAfterMillis);

    @Nonnull
    CompletionStage<Long> del(byte[] key);

    @Nonnull
    CompletionStage<Long> del(byte[][] keys);

    @Nonnull
    CompletionStage<Boolean> flushAll();
}
