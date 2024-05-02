package ru.tinkoff.kora.cache.redis.lettuce;

import io.lettuce.core.*;
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.api.async.RedisKeyAsyncCommands;
import io.lettuce.core.api.async.RedisServerAsyncCommands;
import io.lettuce.core.api.async.RedisStringAsyncCommands;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.codec.ByteArrayCodec;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.cache.redis.RedisCacheClient;
import ru.tinkoff.kora.common.util.TimeUtils;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

final class LettuceRedisCacheClient implements RedisCacheClient {

    private static final Logger logger = LoggerFactory.getLogger(LettuceRedisCacheClient.class);

    private final AbstractRedisClient redisClient;

    private StatefulConnection<byte[], byte[]> connection;

    private RedisStringAsyncCommands<byte[], byte[]> stringCommands;
    private RedisServerAsyncCommands<byte[], byte[]> serverCommands;
    private RedisKeyAsyncCommands<byte[], byte[]> keyCommands;

    LettuceRedisCacheClient(AbstractRedisClient redisClient) {
        this.redisClient = redisClient;
    }

    @Nonnull
    @Override
    public CompletionStage<byte[]> get(byte[] key) {
        return stringCommands.get(key);
    }

    @Nonnull
    @Override
    public CompletionStage<Map<byte[], byte[]>> mget(byte[][] keys) {
        return stringCommands.mget(keys).thenApply(r -> r.stream().collect(Collectors.toMap(KeyValue::getKey, Value::getValue)));
    }

    @Nonnull
    @Override
    public CompletionStage<byte[]> getex(byte[] key, long expireAfterMillis) {
        return stringCommands.getex(key, GetExArgs.Builder.ex(Duration.ofMillis(expireAfterMillis)));
    }

    @Nonnull
    @Override
    public CompletionStage<Boolean> set(byte[] key, byte[] value) {
        return stringCommands.set(key, value).thenApply(r -> true);
    }

    @Override
    public CompletionStage<Boolean> mset(Map<byte[], byte[]> keyAndValue) {
        return stringCommands.mset(keyAndValue).thenApply(r -> true);
    }

    @Nonnull
    @Override
    public CompletionStage<Boolean> psetex(byte[] key, byte[] value, long expireAfterMillis) {
        return stringCommands.psetex(key, expireAfterMillis, value).thenApply(r -> true);
    }

    @Nonnull
    @Override
    public CompletionStage<Long> del(byte[] key) {
        return keyCommands.del(key);
    }

    @Nonnull
    @Override
    public CompletionStage<Long> del(byte[][] keys) {
        return keyCommands.del(keys);
    }

    @Nonnull
    @Override
    public CompletionStage<Boolean> flushAll() {
        return serverCommands.flushall(FlushMode.SYNC).thenApply(r -> true);
    }

    @Override
    public void init() {
        logger.debug("Redis Client (Lettuce) starting...");
        final long started = TimeUtils.started();

        if (redisClient instanceof io.lettuce.core.RedisClient rc) {
            var redisConnection = rc.connect(new ByteArrayCodec());
            this.connection = redisConnection;

            var asyncCommands = redisConnection.async();
            this.keyCommands = asyncCommands;
            this.serverCommands = asyncCommands;
            this.stringCommands = asyncCommands;
        } else if (redisClient instanceof RedisClusterClient rcc) {
            var clusterConnection = rcc.connect(new ByteArrayCodec());
            this.connection = clusterConnection;

            var asyncCommands = clusterConnection.async();
            this.keyCommands = asyncCommands;
            this.serverCommands = asyncCommands;
            this.stringCommands = asyncCommands;
        } else {
            throw new UnsupportedOperationException("Unknown Redis Client: " + redisClient.getClass());
        }

        logger.info("Redis Client (Lettuce) started in {}", TimeUtils.tookForLogging(started));
    }

    @Override
    public void release() {
        logger.debug("Redis Client (Lettuce) stopping...");
        final long started = TimeUtils.started();
        connection.close();
        logger.info("Redis Client (Lettuce) stopped in {}", TimeUtils.tookForLogging(started));
    }
}
