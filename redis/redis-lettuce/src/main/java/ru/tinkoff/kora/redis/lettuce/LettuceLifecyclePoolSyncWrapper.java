package ru.tinkoff.kora.redis.lettuce;

import io.lettuce.core.api.StatefulConnection;
import org.apache.commons.pool2.ObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.common.util.TimeUtils;

final class LettuceLifecyclePoolSyncWrapper<K, V> implements Lifecycle, Wrapped<ObjectPool<StatefulConnection<K, V>>> {

    private static final Logger logger = LoggerFactory.getLogger(LettuceFactory.class);

    private final PoolProvider<K, V> provider;

    private volatile ObjectPool<StatefulConnection<K, V>> pool;

    @FunctionalInterface
    interface PoolProvider<K, V> {
        ObjectPool<StatefulConnection<K, V>> create() throws Exception;
    }

    LettuceLifecyclePoolSyncWrapper(PoolProvider<K, V> provider) {
        this.provider = provider;
    }

    @Override
    public ObjectPool<StatefulConnection<K, V>> value() {
        return this.pool;
    }

    @Override
    public void init() throws Exception {
        logger.debug("Lettuce Redis sync pool starting...");
        final long started = TimeUtils.started();

        this.pool = provider.create();

        logger.info("Lettuce Redis sync pool started in {}", TimeUtils.tookForLogging(started));
    }

    @Override
    public void release() {
        if (this.pool != null) {
            logger.debug("Lettuce Redis sync pool stopping...");
            final long stopping = TimeUtils.started();

            this.pool.close();

            logger.info("Lettuce Redis sync pool stopped in {}", TimeUtils.tookForLogging(stopping));
        }
    }
}
