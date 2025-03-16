package ru.tinkoff.kora.cache.redis;

import jakarta.annotation.Nonnull;

import java.util.Map;
import java.util.concurrent.CompletionStage;

public interface RedisCacheAsyncClient {

    @Nonnull
    CompletionStage<byte[]> get(byte[] key);

    @Nonnull
    CompletionStage<Map<byte[], byte[]>> mget(byte[][] keys);

    @Nonnull
    CompletionStage<byte[]> getex(byte[] key, long expireAfterMillis);

    @Nonnull
    CompletionStage<Map<byte[], byte[]>> getex(byte[][] keys, long expireAfterMillis);

    @Nonnull
    CompletionStage<Void> set(byte[] key, byte[] value);

    @Nonnull
    CompletionStage<Void> mset(@Nonnull Map<byte[], byte[]> keyAndValue);

    @Nonnull
    CompletionStage<Void> psetex(byte[] key, byte[] value, long expireAfterMillis);

    @Nonnull
    CompletionStage<Void> psetex(@Nonnull Map<byte[], byte[]> keyAndValue, long expireAfterMillis);

    @Nonnull
    CompletionStage<Long> del(byte[] key);

    @Nonnull
    CompletionStage<Long> del(byte[][] keys);

    @Nonnull
    CompletionStage<Void> flushAll();
}
