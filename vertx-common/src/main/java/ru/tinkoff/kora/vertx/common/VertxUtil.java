package ru.tinkoff.kora.vertx.common;

import io.netty.channel.EventLoopGroup;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.impl.VertxBuilder;
import io.vertx.core.metrics.MetricsOptions;
import io.vertx.core.spi.VertxThreadFactory;
import ru.tinkoff.kora.netty.common.NettyChannelFactory;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class VertxUtil {

    private VertxUtil() { }

    private static final AtomicInteger threadCounter = new AtomicInteger(0);

    public static Vertx customEventLoopVertx(EventLoopGroup eventLoopGroup, NettyChannelFactory nettyChannelFactory) {
        return new VertxBuilder(new VertxOptions()
            .setWorkerPoolSize(1) // We are not using Vertx workers, but cant be zero
            .setMetricsOptions(new MetricsOptions().setEnabled(false)))
            .executorServiceFactory((threadFactory, concurrency, maxConcurrency) -> eventLoopGroup)
            .transport(new VertxEventLoopGroupTransport(eventLoopGroup, nettyChannelFactory))
            .vertx();
    }

    public static ThreadFactory vertxThreadFactory() {
        return r -> {
            var i = threadCounter.incrementAndGet();
            return VertxThreadFactory.INSTANCE.newVertxThread(r, "netty-event-loop-" + i, false, 1488, TimeUnit.SECONDS);
        };
    }
}
