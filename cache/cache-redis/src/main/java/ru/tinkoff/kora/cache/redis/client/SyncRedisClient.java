package ru.tinkoff.kora.cache.redis.client;

import java.util.Map;

public interface SyncRedisClient {

    byte[] get(byte[] key);

    Map<byte[], byte[]> get(byte[][] keys);

    byte[] getExpire(byte[] key, long expireAfterMillis);

    Map<byte[], byte[]> getExpire(byte[][] keys, long expireAfterMillis);

    void set(byte[] key, byte[] value);

    void setExpire(byte[] key, byte[] value, long expireAfterMillis);

    long del(byte[] key);

    long del(byte[][] keys);

    void flushAll();
}
