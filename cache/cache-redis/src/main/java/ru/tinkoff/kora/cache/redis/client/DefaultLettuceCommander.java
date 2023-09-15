package ru.tinkoff.kora.cache.redis.client;

import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.codec.ByteArrayCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;

import java.time.Duration;

final class DefaultLettuceCommander implements LettuceCommander, Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(DefaultLettuceCommander.class);

    private final AbstractRedisClient redisClient;

    private Sync sync;
    private Reactive reactive;
    private StatefulConnection<byte[], byte[]> connection;

    public DefaultLettuceCommander(AbstractRedisClient redisClient) {
        this.redisClient = redisClient;
    }

    @Override
    public void init() {
        logger.debug("Redis Client (Lettuce) starting...");
        final long started = System.nanoTime();

        if (redisClient instanceof RedisClient) {
            var redisConnection = ((RedisClient) redisClient).connect(new ByteArrayCodec());
            var syncCommands = redisConnection.sync();
            var reactiveCommands = redisConnection.reactive();
            this.sync = new Sync(syncCommands, syncCommands, syncCommands);
            this.reactive = new Reactive(reactiveCommands, reactiveCommands, reactiveCommands);
            this.connection = redisConnection;
        } else if (redisClient instanceof RedisClusterClient) {
            var clusterConnection = ((RedisClusterClient) redisClient).connect(new ByteArrayCodec());
            var syncCommands = clusterConnection.sync();
            var reactiveCommands = clusterConnection.reactive();
            this.sync = new Sync(syncCommands, syncCommands, syncCommands);
            this.reactive = new Reactive(reactiveCommands, reactiveCommands, reactiveCommands);
            this.connection = clusterConnection;
        } else {
            throw new UnsupportedOperationException("Unknown Redis Client: " + redisClient.getClass());
        }

        logger.info("Redis Client (Lettuce) started in {}", Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
    }

    @Override
    public void release() {
        logger.debug("Redis Client (Lettuce) stopping...");
        final long started = System.nanoTime();
        connection.close();
        logger.info("Redis Client (Lettuce) stopped in {}", Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
    }

    @Override
    public Sync sync() {
        return this.sync;
    }

    @Override
    public Reactive reactive() {
        return this.reactive;
    }
}
