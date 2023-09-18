package ru.tinkoff.kora.cache.redis.client;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Map;

public interface SyncRedisClient {

    @Nullable
    byte[] get(byte[] key);

    @Nonnull
    Map<byte[], byte[]> get(byte[][] keys);

    @Nullable
    byte[] getExpire(byte[] key, long expireAfterMillis);

    @Nonnull
    Map<byte[], byte[]> getExpire(byte[][] keys, long expireAfterMillis);

    void set(byte[] key, byte[] value);

    void setExpire(byte[] key, byte[] value, long expireAfterMillis);

    long del(byte[] key);

    long del(byte[][] keys);

    void flushAll();
}
