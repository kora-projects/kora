package ru.tinkoff.kora.cache.redis.lettuce;

import jakarta.annotation.Nonnull;
import ru.tinkoff.kora.cache.redis.RedisCacheAsyncClient;
import ru.tinkoff.kora.cache.redis.RedisCacheClient;

import java.util.Map;

final class LettuceCacheSyncClient implements RedisCacheClient {

    private final RedisCacheAsyncClient redisAsyncClient;

    LettuceCacheSyncClient(RedisCacheAsyncClient redisAsyncClient) {
        this.redisAsyncClient = redisAsyncClient;
    }

    @Nonnull
    @Override
    public byte[] get(byte[] key) {
        return this.redisAsyncClient.get(key).toCompletableFuture().join();
    }

    @Nonnull
    @Override
    public Map<byte[], byte[]> mget(byte[][] keys) {
        return this.redisAsyncClient.mget(keys).toCompletableFuture().join();
    }

    @Nonnull
    @Override
    public byte[] getex(byte[] key, long expireAfterMillis) {
        return this.redisAsyncClient.getex(key, expireAfterMillis).toCompletableFuture().join();
    }

    @Nonnull
    @Override
    public Map<byte[], byte[]> getex(byte[][] keys, long expireAfterMillis) {
        return this.redisAsyncClient.getex(keys, expireAfterMillis).toCompletableFuture().join();
    }

    @Override
    public void set(byte[] key, byte[] value) {
        this.redisAsyncClient.set(key, value).toCompletableFuture().join();
    }

    @Override
    public void mset(@Nonnull Map<byte[], byte[]> keyAndValue) {
        this.redisAsyncClient.mset(keyAndValue).toCompletableFuture().join();
    }

    @Override
    public void psetex(byte[] key, byte[] value, long expireAfterMillis) {
        this.redisAsyncClient.psetex(key, value, expireAfterMillis).toCompletableFuture().join();
    }

    @Override
    public void psetex(@Nonnull Map<byte[], byte[]> keyAndValue, long expireAfterMillis) {
        this.redisAsyncClient.psetex(keyAndValue, expireAfterMillis).toCompletableFuture().join();
    }

    @Override
    public long del(byte[] key) {
        return this.redisAsyncClient.del(key).toCompletableFuture().join();
    }

    @Override
    public long del(byte[][] keys) {
        return this.redisAsyncClient.del(keys).toCompletableFuture().join();
    }

    @Override
    public void flushAll() {
        this.redisAsyncClient.flushAll().toCompletableFuture().join();
    }
}
