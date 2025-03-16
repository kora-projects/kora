package ru.tinkoff.kora.cache.redis;

import jakarta.annotation.Nonnull;

import java.util.Map;

public interface RedisCacheClient {

    @Nonnull
    byte[] get(byte[] key);

    @Nonnull
    Map<byte[], byte[]> mget(byte[][] keys);

    @Nonnull
    byte[] getex(byte[] key, long expireAfterMillis);

    @Nonnull
    Map<byte[], byte[]> getex(byte[][] keys, long expireAfterMillis);

    void set(byte[] key, byte[] value);

    void mset(@Nonnull Map<byte[], byte[]> keyAndValue);

    void psetex(byte[] key, byte[] value, long expireAfterMillis);

    void psetex(@Nonnull Map<byte[], byte[]> keyAndValue, long expireAfterMillis);

    long del(byte[] key);

    long del(byte[][] keys);

    void flushAll();
}
