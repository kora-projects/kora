package ru.tinkoff.kora.redis.lettuce;

import io.lettuce.core.api.StatefulConnection;
import io.lettuce.core.support.AsyncPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.common.util.TimeUtils;

final class LettuceLifecyclePoolAsyncWrapper<K, V> implements Lifecycle, Wrapped<AsyncPool<StatefulConnection<K, V>>> {

    private static final Logger logger = LoggerFactory.getLogger(LettuceFactory.class);

    private final PoolProvider<K, V> provider;

    private volatile AsyncPool<StatefulConnection<K, V>> connection;

    @FunctionalInterface
    interface PoolProvider<K, V> {
        AsyncPool<StatefulConnection<K, V>> create() throws Exception;
    }

    LettuceLifecyclePoolAsyncWrapper(PoolProvider<K, V> provider) {
        this.provider = provider;
    }

    @Override
    public AsyncPool<StatefulConnection<K, V>> value() {
        return this.connection;
    }

    @Override
    public void init() throws Exception {
        logger.debug("Lettuce Redis async pool starting...");
        final long started = TimeUtils.started();

        this.connection = provider.create();

        logger.info("Lettuce Redis async pool started in {}", TimeUtils.tookForLogging(started));
    }

    @Override
    public void release() {
        if (this.connection != null) {
            logger.debug("Lettuce Redis async pool stopping...");
            final long stopping = TimeUtils.started();

            this.connection.close();

            logger.info("Lettuce Redis async pool stopped in {}", TimeUtils.tookForLogging(stopping));
        }
    }
}
