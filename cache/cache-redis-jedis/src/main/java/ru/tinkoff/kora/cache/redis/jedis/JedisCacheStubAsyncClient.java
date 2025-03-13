package ru.tinkoff.kora.cache.redis.jedis;

import jakarta.annotation.Nonnull;
import ru.tinkoff.kora.cache.redis.RedisCacheAsyncClient;
import ru.tinkoff.kora.cache.redis.RedisCacheClient;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

final class JedisCacheStubAsyncClient implements RedisCacheAsyncClient {

    private final RedisCacheClient syncClient;

    JedisCacheStubAsyncClient(RedisCacheClient syncClient) {
        this.syncClient = syncClient;
    }

    @Nonnull
    @Override
    public CompletionStage<byte[]> get(byte[] key) {
        try {
            return CompletableFuture.completedFuture(syncClient.get(key));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Nonnull
    @Override
    public CompletionStage<Map<byte[], byte[]>> mget(byte[][] keys) {
        try {
            return CompletableFuture.completedFuture(syncClient.mget(keys));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Nonnull
    @Override
    public CompletionStage<byte[]> getex(byte[] key, long expireAfterMillis) {
        try {
            return CompletableFuture.completedFuture(syncClient.getex(key, expireAfterMillis));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Nonnull
    @Override
    public CompletionStage<Map<byte[], byte[]>> getex(byte[][] keys, long expireAfterMillis) {
        try {
            return CompletableFuture.completedFuture(syncClient.getex(keys, expireAfterMillis));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Nonnull
    @Override
    public CompletionStage<Void> set(byte[] key, byte[] value) {
        try {
            syncClient.set(key, value);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Nonnull
    @Override
    public CompletionStage<Void> mset(@Nonnull Map<byte[], byte[]> keyAndValue) {
        try {
            syncClient.mset(keyAndValue);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Nonnull
    @Override
    public CompletionStage<Void> psetex(byte[] key, byte[] value, long expireAfterMillis) {
        try {
            syncClient.psetex(key, value, expireAfterMillis);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Nonnull
    @Override
    public CompletionStage<Void> psetex(@Nonnull Map<byte[], byte[]> keyAndValue, long expireAfterMillis) {
        try {
            syncClient.psetex(keyAndValue, expireAfterMillis);
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Nonnull
    @Override
    public CompletionStage<Long> del(byte[] key) {
        try {
            return CompletableFuture.completedFuture(syncClient.del(key));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Nonnull
    @Override
    public CompletionStage<Long> del(byte[][] keys) {
        try {
            return CompletableFuture.completedFuture(syncClient.del(keys));
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Nonnull
    @Override
    public CompletionStage<Void> flushAll() {
        try {
            syncClient.flushAll();
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }
}
