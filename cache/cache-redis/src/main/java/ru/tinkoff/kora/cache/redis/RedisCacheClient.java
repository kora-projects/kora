package ru.tinkoff.kora.cache.redis;


import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public interface RedisCacheClient {
    RedisCacheClientConfig config();

    CompletionStage<List<byte[]>> scan(byte[] prefix);

    CompletionStage<byte[]> get(byte[] key);

    default CompletionStage<Map<byte[], byte[]>> mget(Collection<byte[]> keys) {
        return mget(keys.toArray(new byte[0][]));
    }

    CompletionStage<Map<byte[], byte[]>> mget(byte[][] keys);

    CompletionStage<byte[]> getex(byte[] key, long expireAfterMillis);

    default CompletionStage<Map<byte[], byte[]>> getex(Collection<byte[]> keys, long expireAfterMillis) {
        return getex(keys.toArray(new byte[0][]), expireAfterMillis);
    }

    CompletionStage<Map<byte[], byte[]>> getex(byte[][] keys, long expireAfterMillis);

    CompletionStage<Boolean> set(byte[] key, byte[] value);

    CompletionStage<Boolean> mset(Map<byte[], byte[]> keyAndValue);

    CompletionStage<Boolean> psetex(byte[] key, byte[] value, long expireAfterMillis);

    CompletionStage<Boolean> psetex(Map<byte[], byte[]> keyAndValue, long expireAfterMillis);

    CompletionStage<Long> del(byte[] key);

    default CompletionStage<Long> del(Collection<byte[]> keys) {
        return del(keys.toArray(new byte[0][]));
    }

    CompletionStage<Long> del(byte[][] keys);

    CompletionStage<Boolean> flushAll();

}
