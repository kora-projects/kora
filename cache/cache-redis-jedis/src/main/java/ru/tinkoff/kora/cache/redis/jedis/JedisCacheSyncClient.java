package ru.tinkoff.kora.cache.redis.jedis;

import jakarta.annotation.Nonnull;
import redis.clients.jedis.Response;
import redis.clients.jedis.UnifiedJedis;
import redis.clients.jedis.params.GetExParams;
import ru.tinkoff.kora.cache.redis.RedisCacheClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class JedisCacheSyncClient implements RedisCacheClient {

    private final UnifiedJedis jedis;

    JedisCacheSyncClient(UnifiedJedis jedis) {
        this.jedis = jedis;
    }

    @Nonnull
    @Override
    public byte[] get(byte[] key) {
        return jedis.get(key);
    }

    @Nonnull
    @Override
    public Map<byte[], byte[]> mget(byte[][] keys) {
        List<byte[]> values = jedis.mget(keys);

        int i = 0;
        Map<byte[], byte[]> keysAndValues = new LinkedHashMap<>(values.size() + 1);
        for (byte[] key : keys) {
            byte[] value = values.get(i);
            if (value != null) {
                keysAndValues.put(key, value);
            }
            i++;
        }

        return keysAndValues;
    }

    @Nonnull
    @Override
    public byte[] getex(byte[] key, long expireAfterMillis) {
        return jedis.getEx(key, GetExParams.getExParams().px(expireAfterMillis));
    }

    @Nonnull
    @Override
    public Map<byte[], byte[]> getex(byte[][] keys, long expireAfterMillis) {
        try (var tx = jedis.pipelined()) {
            final Map<byte[], Response<byte[]>> responses = new LinkedHashMap<>();
            for (byte[] key : keys) {
                var response = tx.getEx(key, GetExParams.getExParams().px(expireAfterMillis));
                responses.put(key, response);
            }
            tx.sync();

            final Map<byte[], byte[]> values = new LinkedHashMap<>();
            responses.forEach((k, r) -> {
                byte[] value = r.get();
                if (value != null) {
                    values.put(k, value);
                }
            });

            return values;
        }
    }

    @Override
    public void set(byte[] key, byte[] value) {
        jedis.set(key, value);
    }

    @Override
    public void mset(Map<byte[], byte[]> keyAndValue) {
        var keysAndValues = new ArrayList<byte[]>(keyAndValue.size() * 2);
        for (var entry : keyAndValue.entrySet()) {
            keysAndValues.add(entry.getKey());
            keysAndValues.add(entry.getValue());
        }
        jedis.mset(keysAndValues.toArray(new byte[][]{}));
    }

    @Override
    public void psetex(byte[] key, byte[] value, long expireAfterMillis) {
        jedis.psetex(key, expireAfterMillis, value);
    }

    @Override
    public void psetex(Map<byte[], byte[]> keyAndValue, long expireAfterMillis) {
        try (var pipeline = jedis.pipelined()) {
            for (var entry : keyAndValue.entrySet()) {
                pipeline.psetex(entry.getKey(), expireAfterMillis, entry.getValue());
            }
            pipeline.sync();
        }
    }

    @Override
    public long del(byte[] key) {
        return jedis.del(key);
    }

    @Override
    public long del(byte[][] keys) {
        return jedis.del(keys);
    }

    @Override
    public void flushAll() {
        jedis.flushAll();
    }
}
