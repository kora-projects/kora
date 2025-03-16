package ru.tinkoff.kora.redis.lettuce;

import io.lettuce.core.api.StatefulConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.common.util.TimeUtils;

final class LettuceLifecycleConnectionWrapper<K, V> implements Lifecycle, Wrapped<StatefulConnection<K, V>> {

    private static final Logger logger = LoggerFactory.getLogger(LettuceFactory.class);

    private final ConnectionProvider<K, V> provider;

    private volatile StatefulConnection<K, V> connection;

    @FunctionalInterface
    interface ConnectionProvider<K, V> {
        StatefulConnection<K, V> create() throws Exception;
    }

    LettuceLifecycleConnectionWrapper(ConnectionProvider<K, V> provider) {
        this.provider = provider;
    }

    @Override
    public StatefulConnection<K, V> value() {
        return this.connection;
    }

    @Override
    public void init() throws Exception {
        logger.debug("Lettuce Redis connection starting...");
        final long started = TimeUtils.started();

        this.connection = provider.create();

        logger.info("Lettuce Redis connection started in {}", TimeUtils.tookForLogging(started));
    }

    @Override
    public void release() {
        if (this.connection != null) {
            logger.debug("Lettuce Redis connection stopping...");
            final long stopping = TimeUtils.started();

            this.connection.close();

            logger.info("Lettuce Redis connection stopped in {}", TimeUtils.tookForLogging(stopping));
        }
    }
}
