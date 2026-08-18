package io.koraframework.resilient.distributed.retry.lettuce;

import io.koraframework.application.graph.Lifecycle;
import io.koraframework.common.util.TimeUtils;
import io.koraframework.resilient.distributed.retry.DistributedRetryBudgetClient;
import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.RedisClient;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.api.sync.RedisKeyCommands;
import io.lettuce.core.api.sync.RedisStringCommands;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lettuce-backed {@link DistributedRetryBudgetClient}. Implements the atomic balance primitive with plain Redis commands — a
 * {@code SET key <initial> NX} seed followed by an atomic {@code INCRBYFLOAT}, plus {@code GET} for reads — so no
 * server-side Lua is involved. Supports standalone and cluster clients.
 */
public class LettuceDistributedRetryBudgetClient implements DistributedRetryBudgetClient, Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(LettuceDistributedRetryBudgetClient.class);

    private final AbstractRedisClient redisClient;

    private StatefulConnection<String, String> connection;
    private RedisStringCommands<String, String> stringCommands;
    private RedisKeyCommands<String, String> keyCommands;

    public LettuceDistributedRetryBudgetClient(AbstractRedisClient redisClient) {
        this.redisClient = redisClient;
    }

    @Override
    public double addAndGet(String key, double delta, double initial, long ttlMillis) {
        // seed the balance only if the key is absent, then apply the delta atomically
        this.stringCommands.set(key, Double.toString(initial), SetArgs.Builder.nx());
        final double value = this.stringCommands.incrbyfloat(key, delta);
        this.keyCommands.pexpire(key, ttlMillis);
        return value;
    }

    @Override
    public double get(String key, double initial) {
        final String value = this.stringCommands.get(key);
        return value == null ? initial : Double.parseDouble(value);
    }

    @Override
    public void init() {
        logger.debug("Retry budget Redis client (Lettuce) starting...");
        final long started = TimeUtils.started();

        switch (redisClient) {
            case RedisClient standalone -> {
                var conn = standalone.connect(StringCodec.UTF8);
                this.connection = conn;
                var commands = conn.sync();
                this.stringCommands = commands;
                this.keyCommands = commands;
            }
            case RedisClusterClient cluster -> {
                var conn = cluster.connect(StringCodec.UTF8);
                this.connection = conn;
                var commands = conn.sync();
                this.stringCommands = commands;
                this.keyCommands = commands;
            }
            default -> throw new UnsupportedOperationException(
                "Unsupported Redis client type for Lettuce retry budget: %s; expected RedisClient or RedisClusterClient"
                    .formatted(redisClient.getClass().getName()));
        }

        logger.info("Retry budget Redis client (Lettuce) started in {}", TimeUtils.tookForLogging(started));
    }

    @Override
    public void release() {
        logger.debug("Retry budget Redis client (Lettuce) stopping...");
        final long started = TimeUtils.started();

        if (connection != null) {
            connection.close();
        }

        logger.info("Retry budget Redis client (Lettuce) stopped in {}", TimeUtils.tookForLogging(started));
    }
}
