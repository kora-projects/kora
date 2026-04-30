package io.koraframework.cache.redis;


import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface RedisCacheClient {

    List<byte[]> scan(byte[] prefix);

    byte @Nullable [] get(byte[] key);

    default Map<byte[], byte[]> mget(Collection<byte[]> keys) {
        return mget(keys.toArray(new byte[0][]));
    }

    Map<byte[], byte[]> mget(byte[][] keys);

    byte @Nullable [] getex(byte[] key, long expireAfterMillis);

    default Map<byte[], byte[]> getex(Collection<byte[]> keys, long expireAfterMillis) {
        return getex(keys.toArray(new byte[0][]), expireAfterMillis);
    }

    Map<byte[], byte[]> getex(byte[][] keys, long expireAfterMillis);

    void set(byte[] key, byte[] value);

    void mset(Map<byte[], byte[]> keyAndValue);

    void psetex(byte[] key, byte[] value, long expireAfterMillis);

    void psetex(Map<byte[], byte[]> keyAndValue, long expireAfterMillis);

    long del(byte[] key);

    default long del(Collection<byte[]> keys) {
        return del(keys.toArray(new byte[0][]));
    }

    long del(byte[][] keys);

    void flushAll();
}
