package io.koraframework.cache.redis.lettuce;

import io.koraframework.application.graph.Lifecycle;
import io.koraframework.cache.redis.RedisCacheClient;
import io.koraframework.common.util.TimeUtils;
import io.lettuce.core.*;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.async.RedisAdvancedClusterAsyncCommands;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.support.AsyncConnectionPoolSupport;
import io.lettuce.core.support.BoundedAsyncPool;
import io.lettuce.core.support.BoundedPoolConfig;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;

public class LettuceClusterCacheClient implements RedisCacheClient, Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(LettuceClusterCacheClient.class);
    private static final byte[] ASTERIX = "*".getBytes();

    protected final RedisClusterClient redisClient;

    // use for pipeline commands only cause lettuce have bad performance when using pool
    protected BoundedAsyncPool<StatefulRedisClusterConnection<byte[], byte[]>> pool;
    protected StatefulRedisClusterConnection<byte[], byte[]> connection;

    // always use async cause sync uses JDK Proxy wrapped async impl
    protected RedisAdvancedClusterAsyncCommands<byte[], byte[]> commands;

    public LettuceClusterCacheClient(RedisClusterClient redisClient) {
        this.redisClient = redisClient;
    }

    @Override
    public List<byte[]> scan(byte[] prefix) {
        byte[] prefixWithAsterix = new byte[prefix.length + ASTERIX.length];
        System.arraycopy(prefix, 0, prefixWithAsterix, 0, prefix.length);
        System.arraycopy(ASTERIX, 0, prefixWithAsterix, prefix.length, ASTERIX.length);

        return commands.scan(ScanArgs.Builder.matches(prefixWithAsterix))
            .thenApply(KeyScanCursor::getKeys)
            .toCompletableFuture().join();
    }

    @Nullable
    @Override
    public byte[] get(byte[] key) {
        return commands.get(key).toCompletableFuture().join();
    }

    @Override
    public Map<byte[], byte[]> mget(byte[][] keys) {
        return commands.mget(keys)
            .thenApply(r -> r.stream()
                .filter(Value::hasValue)
                .collect(Collectors.toMap(KeyValue::getKey,
                    Value::getValue,
                    (k1, k2) -> k2,
                    LinkedHashMap::new)))
            .toCompletableFuture().join();
    }

    @Nullable
    @Override
    public byte[] getex(byte[] key, long expireAfterMillis) {
        return commands.getex(key, GetExArgs.Builder.px(expireAfterMillis)).toCompletableFuture().join();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<byte[], byte[]> getex(Collection<byte[]> keys, long expireAfterMillis) {
        return pool.acquire().thenCompose(connection -> {
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

            return pool.release(connection)
                .thenCompose(_v -> CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)))
                .thenApply(_void -> futures.stream()
                    .map(f -> f.getNow(null))
                    .filter(Objects::nonNull)
                    .map(v -> ((Map.Entry<byte[], byte[]>) v))
                    .collect(Collectors.toMap(Map.Entry::getKey,
                        Map.Entry::getValue,
                        (k1, k2) -> k2,
                        LinkedHashMap::new)));
        }).join();
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<byte[], byte[]> getex(byte[][] keys, long expireAfterMillis) {
        return pool.acquire().thenCompose(connection -> {
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

            return pool.release(connection)
                .thenCompose(_v -> CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)))
                .thenApply(_void -> futures.stream()
                    .map(f -> f.getNow(null))
                    .filter(Objects::nonNull)
                    .map(v -> ((Map.Entry<byte[], byte[]>) v))
                    .collect(Collectors.toMap(Map.Entry::getKey,
                        Map.Entry::getValue,
                        (k1, k2) -> k2,
                        LinkedHashMap::new)));
        }).join();
    }

    @Override
    public void set(byte[] key, byte[] value) {
        commands.set(key, value).toCompletableFuture().join();
    }

    @Override
    public void mset(Map<byte[], byte[]> keyAndValue) {
        commands.mset(keyAndValue).toCompletableFuture().join();
    }

    @Override
    public void psetex(byte[] key, byte[] value, long expireAfterMillis) {
        commands.psetex(key, expireAfterMillis, value).toCompletableFuture().join();
    }

    @Override
    public void psetex(Map<byte[], byte[]> keyAndValue, long expireAfterMillis) {
        try {
            pool.acquire().thenCompose(connection -> {
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

                return pool.release(connection)
                    .thenCompose(_v -> CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)));
            }).join();
        } catch (Exception e) {
            if (e instanceof CompletionException ce) {
                if (ce.getCause() instanceof RuntimeException re) {
                    throw re;
                } else {
                    throw ce;
                }
            } else {
                throw e;
            }
        }
    }

    @Override
    public long del(byte[] key) {
        return commands.del(key).toCompletableFuture().join();
    }

    @Override
    public long del(byte[][] keys) {
        return commands.del(keys).toCompletableFuture().join();
    }

    @Override
    public void flushAll() {
        commands.flushall(FlushMode.SYNC)
            .toCompletableFuture().join();
    }

    @Override
    public void init() {
        try {
            logger.debug("Redis Cluster Client (Lettuce) starting...");
            final long started = TimeUtils.started();

            final BoundedPoolConfig poolConfig = BoundedPoolConfig.builder()
                .maxTotal(Math.max(Runtime.getRuntime().availableProcessors(), 1) * 8)
                .maxIdle(Math.max(Runtime.getRuntime().availableProcessors(), 1) * 4)
                .minIdle(0)
                .testOnAcquire(false)
                .testOnCreate(false)
                .testOnRelease(false)
                .build();

            this.pool = AsyncConnectionPoolSupport.createBoundedObjectPool(() -> redisClient.connectAsync(ByteArrayCodec.INSTANCE), poolConfig, false);
            this.connection = redisClient.connect(ByteArrayCodec.INSTANCE);
            this.commands = this.connection.async();

            logger.info("Redis Cluster Client (Lettuce) started in {}", TimeUtils.tookForLogging(started));
        } catch (Exception e) {
            throw new RuntimeException("Redis Client (Lettuce) failed to start in cluster mode, due to: " + e.getMessage(), e);
        }
    }

    @Override
    public void release() {
        logger.debug("Redis Cluster Client (Lettuce) stopping...");
        final long started = TimeUtils.started();

        this.connection.close();
        this.pool.close();

        logger.info("Redis Cluster Client (Lettuce) stopped in {}", TimeUtils.tookForLogging(started));
    }
}
