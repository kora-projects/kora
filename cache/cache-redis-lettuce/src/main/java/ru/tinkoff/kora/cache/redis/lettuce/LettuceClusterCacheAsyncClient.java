package ru.tinkoff.kora.cache.redis.lettuce;

import io.lettuce.core.FlushMode;
import io.lettuce.core.GetExArgs;
import io.lettuce.core.KeyValue;
import io.lettuce.core.Value;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.support.AsyncConnectionPoolSupport;
import io.lettuce.core.support.BoundedAsyncPool;
import io.lettuce.core.support.BoundedPoolConfig;
import jakarta.annotation.Nonnull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.cache.redis.RedisCacheAsyncClient;
import ru.tinkoff.kora.common.util.TimeUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

final class LettuceClusterCacheAsyncClient implements RedisCacheAsyncClient, Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(LettuceClusterCacheAsyncClient.class);

    // always use async cause sync uses JDK Proxy wrapped async impl
    private final RedisClusterAsyncCommands<byte[], byte[]> lettuceCommands;
    private final RedisClusterClient lettuceClient;

    // use for pipeline commands only cause lettuce have bad performance when using pool
    private BoundedAsyncPool<StatefulRedisClusterConnection<byte[], byte[]>> lettucePool;


    LettuceClusterCacheAsyncClient(RedisClusterClient lettuceClient,
                                   RedisClusterAsyncCommands<byte[], byte[]> lettuceCommands) {
        this.lettuceClient = lettuceClient;
        this.lettuceCommands = lettuceCommands;
    }

    @Nonnull
    @Override
    public CompletionStage<byte[]> get(byte[] key) {
        return lettuceCommands.get(key);
    }

    @Nonnull
    @Override
    public CompletionStage<Map<byte[], byte[]>> mget(byte[][] keys) {
        return lettuceCommands.mget(keys)
            .thenApply(r -> r.stream()
                .filter(Value::hasValue)
                .collect(Collectors.toMap(KeyValue::getKey, Value::getValue, (x, y) -> x, LinkedHashMap::new)));
    }

    @Nonnull
    @Override
    public CompletionStage<byte[]> getex(byte[] key, long expireAfterMillis) {
        return lettuceCommands.getex(key, GetExArgs.Builder.px(expireAfterMillis));
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    @Override
    public CompletionStage<Map<byte[], byte[]>> getex(byte[][] keys, long expireAfterMillis) {
        return lettucePool.acquire().thenCompose(connection -> {
            connection.setAutoFlushCommands(false);

            List<CompletableFuture<?>> futures = new ArrayList<>();

            var async = connection.async();
            for (byte[] key : keys) {
                var future = async.getex(key, GetExArgs.Builder.px(expireAfterMillis))
                    .thenApply(v -> (v == null) ? null : Map.entry(key, v))
                    .toCompletableFuture();

                futures.add(future);
            }

            connection.flushCommands();
            connection.setAutoFlushCommands(true);

            return lettucePool.release(connection)
                .thenCompose(_v -> CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)))
                .thenApply(_void -> futures.stream()
                    .map(f -> f.getNow(null))
                    .filter(Objects::nonNull)
                    .map(v -> ((Map.Entry<byte[], byte[]>) v))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (x, y) -> x, LinkedHashMap::new)));
        });
    }

    @Nonnull
    @Override
    public CompletionStage<Void> set(byte[] key, byte[] value) {
        return lettuceCommands.set(key, value).thenApply(r -> null);
    }

    @Override
    public CompletionStage<Void> mset(Map<byte[], byte[]> keyAndValue) {
        return lettuceCommands.mset(keyAndValue).thenApply(r -> null);
    }

    @Nonnull
    @Override
    public CompletionStage<Void> psetex(byte[] key, byte[] value, long expireAfterMillis) {
        return lettuceCommands.psetex(key, expireAfterMillis, value).thenApply(r -> null);
    }

    @Nonnull
    @Override
    public CompletionStage<Void> psetex(Map<byte[], byte[]> keyAndValue, long expireAfterMillis) {
        return lettucePool.acquire().thenCompose(connection -> {
            connection.setAutoFlushCommands(false);

            List<CompletableFuture<?>> futures = new ArrayList<>();

            var async = connection.async();
            for (Map.Entry<byte[], byte[]> entry : keyAndValue.entrySet()) {
                var future = async.psetex(entry.getKey(), expireAfterMillis, entry.getValue())
                    .toCompletableFuture();

                futures.add(future);
            }

            connection.flushCommands();
            connection.setAutoFlushCommands(true);

            return lettucePool.release(connection)
                .thenCompose(_v -> CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)));
        });
    }

    @Nonnull
    @Override
    public CompletionStage<Long> del(byte[] key) {
        return lettuceCommands.del(key);
    }

    @Nonnull
    @Override
    public CompletionStage<Long> del(byte[][] keys) {
        return lettuceCommands.del(keys);
    }

    @Nonnull
    @Override
    public CompletionStage<Void> flushAll() {
        return lettuceCommands.flushall(FlushMode.SYNC).thenApply(r -> null);
    }

    @Override
    public void init() {
        logger.debug("Redis Client (Lettuce) starting...");
        final long started = TimeUtils.started();

        final BoundedPoolConfig poolConfig = BoundedPoolConfig.builder()
            .maxTotal(Math.max(Runtime.getRuntime().availableProcessors(), 1) * 4)
            .maxIdle(Math.max(Runtime.getRuntime().availableProcessors(), 1) * 4)
            .minIdle(0)
            .testOnAcquire(false)
            .testOnCreate(false)
            .testOnRelease(false)
            .build();

        this.lettucePool = AsyncConnectionPoolSupport.createBoundedObjectPool(() -> lettuceClient.connectAsync(ByteArrayCodec.INSTANCE), poolConfig, false);

        logger.info("Redis Client (Lettuce) started in {}", TimeUtils.tookForLogging(started));
    }

    @Override
    public void release() {
        logger.debug("Redis Client (Lettuce) stopping...");
        final long started = TimeUtils.started();

        this.lettucePool.close();

        logger.info("Redis Client (Lettuce) stopped in {}", TimeUtils.tookForLogging(started));
    }
}
