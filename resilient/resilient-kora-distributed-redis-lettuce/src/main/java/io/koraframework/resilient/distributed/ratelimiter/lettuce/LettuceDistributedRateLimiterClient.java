package io.koraframework.resilient.distributed.ratelimiter.lettuce;

import io.koraframework.application.graph.Lifecycle;
import io.koraframework.common.util.TimeUtils;
import io.koraframework.resilient.distributed.ratelimiter.DistributedRateLimiterClient;
import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.RedisClient;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.api.sync.RedisKeyCommands;
import io.lettuce.core.api.sync.RedisStringCommands;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.codec.StringCodec;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lettuce-backed {@link DistributedRateLimiterClient}. Implements the counter primitives with plain Redis commands —
 * {@code INCR}/{@code INCRBY} plus {@code PEXPIRE}, and {@code SET ... PX} — so no server-side Lua is involved.
 *
 * <p>Supports both standalone ({@link RedisClient}) and cluster ({@link RedisClusterClient}) clients; the counter key is
 * used as the Redis key, so cluster slotting works out of the box.
 */
public class LettuceDistributedRateLimiterClient implements DistributedRateLimiterClient, Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(LettuceDistributedRateLimiterClient.class);

    private final AbstractRedisClient redisClient;

    @Nullable
    private StatefulConnection<String, String> connection;
    @Nullable
    private RedisStringCommands<String, String> stringCommands;
    @Nullable
    private RedisKeyCommands<String, String> keyCommands;

    public LettuceDistributedRateLimiterClient(AbstractRedisClient redisClient) {
        this.redisClient = redisClient;
    }

    @Override
    public long incrementAndExpire(String key, long ttlMillis) {
        final long count = this.stringCommands.incr(key);
        // fresh window counters start at 1; set the TTL once when the key is created
        if (count == 1L) {
            this.keyCommands.pexpire(key, ttlMillis);
        }
        return count;
    }

    @Override
    public long addAndExpire(String key, long delta, long ttlMillis) {
        final long value = this.stringCommands.incrby(key, delta);
        this.keyCommands.pexpire(key, ttlMillis);
        return value;
    }

    @Override
    public void set(String key, long value, long ttlMillis) {
        this.stringCommands.set(key, Long.toString(value), SetArgs.Builder.px(ttlMillis));
    }

    @Override
    public void init() {
        logger.debug("Redis Ratelimiter client (Lettuce) starting...");
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
                "Unsupported Redis client type for Lettuce rate limiter: %s; expected RedisClient or RedisClusterClient"
                    .formatted(redisClient.getClass().getName()));
        }

        logger.info("Redis Ratelimiter client (Lettuce) started in {}", TimeUtils.tookForLogging(started));
    }

    @Override
    public void release() {
        logger.debug("Redis Ratelimiter client (Lettuce) stopping...");
        final long started = TimeUtils.started();

        if (connection != null) {
            connection.close();
        }

        logger.info("Redis Ratelimiter client (Lettuce) stopped in {}", TimeUtils.tookForLogging(started));
    }
}
