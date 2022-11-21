package ru.tinkoff.kora.vertx.common;

import io.netty.channel.EventLoopGroup;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.impl.VertxBuilder;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.core.spi.VertxThreadFactory;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class VertxUtil {
    private static final AtomicInteger threadCounter = new AtomicInteger(0);

    private VertxUtil() {}

    public static Vertx customEventLoopVertx(EventLoopGroup eventLoopGroup) {
        return new VertxBuilder(new VertxOptions()
            .setWorkerPoolSize(1)
            .setMetricsOptions(new MetricsOptions().setEnabled(false)))
            .executorServiceFactory((threadFactory, concurrency, maxConcurrency) -> eventLoopGroup)
            .transport(new VertxEventLoopGroupTransport(eventLoopGroup))
            .vertx();
    }

    public static ThreadFactory vertxThreadFactory() {
        return r -> {
            var i = threadCounter.incrementAndGet();
            return VertxThreadFactory.INSTANCE.newVertxThread(r, "netty-event-loop-" + i, false, 1488, TimeUnit.SECONDS);
        };
    }
}
