package ru.tinkoff.kora.cache.redis.client;

public interface SyncRedisClient {

    byte[] get(byte[] key);

    byte[] getExpire(byte[] key, long expireAfterMillis);

    void set(byte[] key, byte[] value);

    void setExpire(byte[] key, byte[] value, long expireAfterMillis);

    void del(byte[] key);

    void flushAll();
}
