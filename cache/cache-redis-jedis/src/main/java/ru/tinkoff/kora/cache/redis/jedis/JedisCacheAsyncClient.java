package ru.tinkoff.kora.cache.redis.jedis;

import jakarta.annotation.Nonnull;
import ru.tinkoff.kora.cache.redis.RedisCacheAsyncClient;
import ru.tinkoff.kora.cache.redis.RedisCacheClient;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

final class JedisCacheAsyncClient implements RedisCacheAsyncClient {

    private final RedisCacheClient syncClient;
    private final Executor executor;

    JedisCacheAsyncClient(RedisCacheClient syncClient, Executor executor) {
        this.syncClient = syncClient;
        this.executor = executor;
    }

    @Nonnull
    @Override
    public CompletionStage<byte[]> get(byte[] key) {
        return CompletableFuture.supplyAsync(() -> syncClient.get(key), executor);
    }

    @Nonnull
    @Override
    public CompletionStage<Map<byte[], byte[]>> mget(byte[][] keys) {
        return CompletableFuture.supplyAsync(() -> syncClient.mget(keys), executor);
    }

    @Nonnull
    @Override
    public CompletionStage<byte[]> getex(byte[] key, long expireAfterMillis) {
        return CompletableFuture.supplyAsync(() -> syncClient.getex(key, expireAfterMillis), executor);
    }

    @Nonnull
    @Override
    public CompletionStage<Map<byte[], byte[]>> getex(byte[][] keys, long expireAfterMillis) {
        return CompletableFuture.supplyAsync(() -> syncClient.getex(keys, expireAfterMillis), executor);
    }

    @Nonnull
    @Override
    public CompletionStage<Void> set(byte[] key, byte[] value) {
        return CompletableFuture.supplyAsync(() -> {
            syncClient.set(key, value);
            return null;
        }, executor);
    }

    @Nonnull
    @Override
    public CompletionStage<Void> mset(@Nonnull Map<byte[], byte[]> keyAndValue) {
        return CompletableFuture.supplyAsync(() -> {
            syncClient.mset(keyAndValue);
            return null;
        }, executor);
    }

    @Nonnull
    @Override
    public CompletionStage<Void> psetex(byte[] key, byte[] value, long expireAfterMillis) {
        return CompletableFuture.supplyAsync(() -> {
            syncClient.psetex(key, value, expireAfterMillis);
            return null;
        }, executor);
    }

    @Nonnull
    @Override
    public CompletionStage<Void> psetex(@Nonnull Map<byte[], byte[]> keyAndValue, long expireAfterMillis) {
        return CompletableFuture.supplyAsync(() -> {
            syncClient.psetex(keyAndValue, expireAfterMillis);
            return null;
        }, executor);
    }

    @Nonnull
    @Override
    public CompletionStage<Long> del(byte[] key) {
        return CompletableFuture.supplyAsync(() -> syncClient.del(key), executor);
    }

    @Nonnull
    @Override
    public CompletionStage<Long> del(byte[][] keys) {
        return CompletableFuture.supplyAsync(() -> syncClient.del(keys), executor);
    }

    @Nonnull
    @Override
    public CompletionStage<Void> flushAll() {
        return CompletableFuture.supplyAsync(() -> {
            syncClient.flushAll();
            return null;
        }, executor);
    }
}
