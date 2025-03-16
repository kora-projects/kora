package ru.tinkoff.kora.redis.lettuce;

import io.lettuce.core.AbstractRedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.cluster.api.async.RedisClusterAsyncCommands;
import io.lettuce.core.cluster.api.reactive.RedisClusterReactiveCommands;
import io.lettuce.core.cluster.api.sync.RedisClusterCommands;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.support.AsyncConnectionPoolSupport;
import io.lettuce.core.support.AsyncPool;
import io.lettuce.core.support.BoundedPoolConfig;
import io.lettuce.core.support.ConnectionPoolSupport;
import org.apache.commons.pool2.ObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;

import java.nio.ByteBuffer;
import java.util.List;

public interface LettuceModule {

    default LettuceConfig lettuceConfig(Config config, ConfigValueExtractor<LettuceConfig> extractor) {
        var value = config.get("lettuce");
        return extractor.extract(value);
    }

    default AbstractRedisClient lettuceClient(LettuceConfig config) {
        return LettuceFactory.build(config);
    }

    @DefaultComponent
    default RedisCodec<Void, Void> lettuceRedisVoidCodec() {
        return LettuceVoidCodec.INSTANCE;
    }

    @DefaultComponent
    default RedisCodec<byte[], byte[]> lettuceRedisByteArrayCodec() {
        return ByteArrayCodec.INSTANCE;
    }

    @DefaultComponent
    default RedisCodec<ByteBuffer, ByteBuffer> lettuceRedisByteBufferCodec() {
        return LettuceByteBufferCodec.INSTANCE;
    }

    @DefaultComponent
    default RedisCodec<String, String> lettuceRedisStringCodec() {
        return StringCodec.UTF8;
    }

    @DefaultComponent
    default RedisCodec<Long, Long> lettuceRedisLongCodec() {
        return LettuceLongCodec.INSTANCE;
    }

    @DefaultComponent
    default RedisCodec<Integer, Integer> lettuceRedisIntegerCodec() {
        return LettuceIntegerCodec.INSTANCE;
    }

    @DefaultComponent
    default <K, V> RedisCodec<K, V> lettuceRedisCompositeCodec(RedisCodec<K, K> keyCodec,
                                                               RedisCodec<V, V> valueCodec) {
        return new LettuceCompositeRedisCodec<>(keyCodec, valueCodec);
    }

    @DefaultComponent
    default <K, V> Wrapped<StatefulConnection<K, V>> lettuceStatefulConnection(AbstractRedisClient redisClient,
                                                                               RedisCodec<K, V> codec) {
        if (redisClient instanceof io.lettuce.core.RedisClient rc) {
            return new LettuceLifecycleConnectionWrapper<>(() -> rc.connect(codec));
        } else if (redisClient instanceof RedisClusterClient rcc) {
            return new LettuceLifecycleConnectionWrapper<>(() -> rcc.connect(codec));
        } else {
            throw new UnsupportedOperationException("Unknown Redis Client: " + redisClient.getClass());
        }
    }

    @DefaultComponent
    default <K, V> Wrapped<ObjectPool<StatefulConnection<K, V>>> lettuceSyncConnectionPool(AbstractRedisClient redisClient,
                                                                                           LettuceConfig lettuceConfig,
                                                                                           RedisCodec<K, V> codec) {
        final GenericObjectPoolConfig<StatefulConnection<K, V>> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setMaxTotal(lettuceConfig.pool().maxTotal());
        poolConfig.setMaxIdle(lettuceConfig.pool().maxIdle());
        poolConfig.setMinIdle(lettuceConfig.pool().minIdle());
        poolConfig.setTestOnBorrow(lettuceConfig.pool().validateOnAcquire());
        poolConfig.setTestOnCreate(lettuceConfig.pool().validateOnCreate());
        poolConfig.setTestOnReturn(lettuceConfig.pool().validateOnRelease());

        if (redisClient instanceof io.lettuce.core.RedisClient rc) {
            final List<RedisURI> redisURIs = LettuceFactory.buildRedisURI(lettuceConfig);
            var redisURI = redisURIs.size() == 1 ? redisURIs.get(0) : null;
            return new LettuceLifecyclePoolSyncWrapper<>(() -> ConnectionPoolSupport.createGenericObjectPool(() -> rc.connect(codec, redisURI), poolConfig));
        } else if (redisClient instanceof RedisClusterClient rcc) {
            return new LettuceLifecyclePoolSyncWrapper<>(() -> ConnectionPoolSupport.createGenericObjectPool(() -> rcc.connect(codec), poolConfig, false));
        } else {
            throw new UnsupportedOperationException("Unknown Redis Client: " + redisClient.getClass());
        }
    }

    @DefaultComponent
    default <K, V> Wrapped<AsyncPool<StatefulConnection<K, V>>> lettuceAsyncConnectionPool(AbstractRedisClient redisClient,
                                                                                           LettuceConfig lettuceConfig,
                                                                                           RedisCodec<K, V> codec) {
        final BoundedPoolConfig poolConfig = BoundedPoolConfig.builder()
            .maxTotal(lettuceConfig.pool().maxTotal())
            .maxIdle(lettuceConfig.pool().maxIdle())
            .minIdle(lettuceConfig.pool().minIdle())
            .testOnAcquire(lettuceConfig.pool().validateOnAcquire())
            .testOnCreate(lettuceConfig.pool().validateOnCreate())
            .testOnRelease(lettuceConfig.pool().validateOnRelease())
            .build();

        if (redisClient instanceof io.lettuce.core.RedisClient rc) {
            final List<RedisURI> redisURIs = LettuceFactory.buildRedisURI(lettuceConfig);
            var redisURI = redisURIs.size() == 1 ? redisURIs.get(0) : null;
            return new LettuceLifecyclePoolAsyncWrapper<>(() -> AsyncConnectionPoolSupport.createBoundedObjectPool(() -> rc.connectAsync(codec, redisURI).thenApply(v -> v), poolConfig));
        } else if (redisClient instanceof RedisClusterClient rcc) {
            return new LettuceLifecyclePoolAsyncWrapper<>(() -> AsyncConnectionPoolSupport.createBoundedObjectPool(() -> rcc.connectAsync(codec).thenApply(v -> v), poolConfig, false));
        } else {
            throw new UnsupportedOperationException("Unknown Redis Client: " + redisClient.getClass());
        }
    }

    @DefaultComponent
    default <K, V> RedisClusterCommands<K, V> lettuceRedisClusterSyncCommands(StatefulConnection<K, V> connection) {
        if (connection instanceof StatefulRedisConnection<K, V> rc) {
            return rc.sync();
        } else if (connection instanceof StatefulRedisClusterConnection<K, V> rcc) {
            return rcc.sync();
        } else {
            throw new UnsupportedOperationException("Unknown Redis Connection: " + connection.getClass());
        }
    }

    @DefaultComponent
    default <K, V> RedisClusterAsyncCommands<K, V> lettuceRedisClusterAsyncCommands(StatefulConnection<K, V> connection) {
        if (connection instanceof StatefulRedisConnection<K, V> rc) {
            return rc.async();
        } else if (connection instanceof StatefulRedisClusterConnection<K, V> rcc) {
            return rcc.async();
        } else {
            throw new UnsupportedOperationException("Unknown Redis Connection: " + connection.getClass());
        }
    }

    @DefaultComponent
    default <K, V> RedisClusterReactiveCommands<K, V> lettuceRedisClusterReactiveCommands(StatefulConnection<K, V> connection) {
        if (connection instanceof StatefulRedisConnection<K, V> rc) {
            return rc.reactive();
        } else if (connection instanceof StatefulRedisClusterConnection<K, V> rcc) {
            return rcc.reactive();
        } else {
            throw new UnsupportedOperationException("Unknown Redis Connection: " + connection.getClass());
        }
    }
}
