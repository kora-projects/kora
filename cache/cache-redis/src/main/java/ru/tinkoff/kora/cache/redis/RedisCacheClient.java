package ru.tinkoff.kora.cache.redis;

import jakarta.annotation.Nonnull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;

public interface RedisCacheClient {
    RedisCacheClientConfig config();

    @Nonnull
    CompletionStage<List<byte[]>> scan(byte[] prefix);

    @Nonnull
    CompletionStage<byte[]> get(byte[] key);

    @Nonnull
    default CompletionStage<Map<byte[], byte[]>> mget(Collection<byte[]> keys) {
        return mget(keys.toArray(new byte[0][]));
    }

    @Nonnull
    CompletionStage<Map<byte[], byte[]>> mget(byte[][] keys);

    @Nonnull
    CompletionStage<byte[]> getex(byte[] key, long expireAfterMillis);

    @Nonnull
    default CompletionStage<Map<byte[], byte[]>> getex(Collection<byte[]> keys, long expireAfterMillis) {
        return getex(keys.toArray(new byte[0][]), expireAfterMillis);
    }

    @Nonnull
    CompletionStage<Map<byte[], byte[]>> getex(byte[][] keys, long expireAfterMillis);

    @Nonnull
    CompletionStage<Boolean> set(byte[] key, byte[] value);

    @Nonnull
    CompletionStage<Boolean> mset(@Nonnull Map<byte[], byte[]> keyAndValue);

    @Nonnull
    CompletionStage<Boolean> psetex(byte[] key, byte[] value, long expireAfterMillis);

    @Nonnull
    CompletionStage<Boolean> psetex(@Nonnull Map<byte[], byte[]> keyAndValue, long expireAfterMillis);

    @Nonnull
    CompletionStage<Long> del(byte[] key);

    @Nonnull
    default CompletionStage<Long> del(Collection<byte[]> keys) {
        return del(keys.toArray(new byte[0][]));
    }

    @Nonnull
    CompletionStage<Long> del(byte[][] keys);

    @Nonnull
    CompletionStage<Boolean> flushAll();

}
