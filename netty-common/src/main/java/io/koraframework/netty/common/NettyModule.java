package io.koraframework.netty.common;

import io.koraframework.application.graph.*;
import io.koraframework.common.annotation.*;
import io.koraframework.config.common.*;
import io.koraframework.config.common.mapper.*;
import io.koraframework.netty.common.NettyTransportConfig.*;
import io.koraframework.netty.common.loom.*;
import io.netty.channel.*;
import io.netty.channel.epoll.*;
import io.netty.channel.kqueue.*;
import io.netty.channel.nio.*;
import io.netty.channel.uring.*;
import io.netty.util.*;
import io.netty.util.concurrent.*;
import org.jspecify.annotations.*;

import java.util.concurrent.*;

public interface NettyModule {

    final class EventLoopBoss {
        private EventLoopBoss() {}
    }

    final class EventLoopWorker {
        private EventLoopWorker() {}
    }

    default NettyTransportConfig nettyTransportConfig(Config config, ConfigValueMapper<NettyTransportConfig> mapper) {
        return mapper.mapOrThrow(config.get("netty"));
    }

    default LoomCarrierConfig loomCarrierConfig(Config config, ConfigValueMapper<LoomCarrierConfig> mapper) {
        return mapper.mapOrThrow(config.get("netty.loom"));
    }

    @Tag(EventLoopBoss.class)
    @DefaultComponent
    default NettyEventLoopFactory nettyEventLoopBossFactory(@Tag(EventLoopBoss.class) @Nullable ThreadFactory threadFactory,
                                                            NettyTransportConfig config) {
        var tf = (threadFactory == null) ? new DefaultThreadFactory("netty-kora-boss", true) : threadFactory;
        return getNettyEventLoopFactory(1, tf, config);
    }

    @Tag(EventLoopBoss.class)
    @DefaultComponent
    default LifecycleWrapper<EventLoopGroup> nettyEventLoopBossGroup(@Tag(EventLoopBoss.class) NettyEventLoopFactory nettyEventLoopFactory) {
        return new LifecycleWrapper<>(
            nettyEventLoopFactory.build(),
            NoopEventLoopConsumer.INSTANCE,
            ShutdownEventLoopConsumer.INSTANCE
        );
    }

    final class NoopEventLoopConsumer implements LifecycleWrapper.ThrowingConsumer<EventLoopGroup> {
        static final NoopEventLoopConsumer INSTANCE = new NoopEventLoopConsumer();

        private NoopEventLoopConsumer() {
        }

        @Override
        public void accept(EventLoopGroup elg) {
        }
    }

    final class ShutdownEventLoopConsumer implements LifecycleWrapper.ThrowingConsumer<EventLoopGroup> {
        static final ShutdownEventLoopConsumer INSTANCE = new ShutdownEventLoopConsumer();

        private ShutdownEventLoopConsumer() {
        }

        @Override
        public void accept(EventLoopGroup elg) throws Exception {
// we don't have to wait because graph will shutdown loop after all the dependent components
            elg.shutdownGracefully(1, 1, TimeUnit.MILLISECONDS).get();
        }
    }

    @DefaultComponent
    default ThreadFactory koraVirtualThreadFactory(
        LoomCarrierConfig loomConfig,
        EventLoopLoomFactory eventLoopLoomFactory) {

        return eventLoopLoomFactory.createVirtualThreadFactory(
            "kora-virtual-executor-",
            true,
            loomConfig.enabled()
        );
    }

    @DefaultComponent
    default EventLoopLoomFactory eventLoopLoomFactory(LoomCarrierConfig config) {
        return new EventLoopLoomFactory(config.enabled());
    }

    @DefaultComponent
    default EventLoopGroupFactory eventLoopGroupFactory(LoomCarrierConfig loomConfig, EventLoopLoomFactory eventLoopLoomFactory) {
        return new EventLoopGroupFactory(loomConfig, eventLoopLoomFactory);
    }

    @Tag(EventLoopWorker.class)
    @DefaultComponent
    default NettyEventLoopFactory nettyEventLoopWorkerFactory(
        @Tag(EventLoopWorker.class) @Nullable ThreadFactory threadFactory,
        NettyTransportConfig config,
        LoomCarrierConfig loomConfig,
        EventLoopGroupFactory eventLoopGroupFactory) {

        int nThreads = config.threads() > 0 ? config.threads() : NettyRuntime.availableProcessors();
        var ioHandlerFactory = getIoHandlerFactory(config);
        if (loomConfig.enabled()) {
            return new LoomEventLoopFactory(eventLoopGroupFactory, nThreads, "netty-kora-loom-worker", true, ioHandlerFactory);
        }

        var tf = (threadFactory == null) ? new DefaultThreadFactory("netty-kora-worker", true) : threadFactory;
        return getNettyEventLoopFactory(nThreads, tf, config);
    }

    @Tag(EventLoopWorker.class)
    @DefaultComponent
    default LifecycleWrapper<EventLoopGroup> nettyEventLoopWorkerGroup(@Tag(EventLoopWorker.class) NettyEventLoopFactory nettyEventLoopFactory) {
        return new LifecycleWrapper<>(
            nettyEventLoopFactory.build(),
            NoopEventLoopConsumer.INSTANCE,
            ShutdownEventLoopConsumer.INSTANCE
        );
    }

    @DefaultComponent
    default NettyChannelFactory nettyChannelFactory(NettyTransportConfig nettyConfig) {
        if (nettyConfig.transport() != null) {
            if (EventLoopType.URING == nettyConfig.transport() && isUringAvailable()) {
                return getUringChannelFactory();
            } else if (EventLoopType.EPOLL == nettyConfig.transport() && isEpollAvailable()) {
                return getEpollChannelFactory();
            } else if (EventLoopType.KQUEUE == nettyConfig.transport() && isKQueueAvailable()) {
                return getKQueueChannelFactory();
            } else if (EventLoopType.NIO == nettyConfig.transport()) {
                return getNioChannelFactory();
            }
        }

// default behavior
        if (isUringAvailable()) {
            return getUringChannelFactory();
        } else if (isEpollAvailable()) {
            return getEpollChannelFactory();
        } else if (isKQueueAvailable()) {
            return getKQueueChannelFactory();
        } else {
            return getNioChannelFactory();
        }
    }

    private static IoHandlerFactory getIoHandlerFactory(NettyTransportConfig config) {
        var transport = config.transport();
        if (transport != null) {
            if (transport == NettyTransportConfig.EventLoopType.URING && isUringAvailable()) {
                return IoUringIoHandler.newFactory();
            } else if (transport == NettyTransportConfig.EventLoopType.EPOLL && isEpollAvailable()) {
                return EpollIoHandler.newFactory();
            } else if (transport == NettyTransportConfig.EventLoopType.KQUEUE && isKQueueAvailable()) {
                return KQueueIoHandler.newFactory();
            } else if (transport == NettyTransportConfig.EventLoopType.NIO) {
                return NioIoHandler.newFactory();
            }
        }

        if (isUringAvailable()) {
            return IoUringIoHandler.newFactory();
        } else if (isEpollAvailable()) {
            return EpollIoHandler.newFactory();
        } else if (isKQueueAvailable()) {
            return KQueueIoHandler.newFactory();
        } else {
            return NioIoHandler.newFactory();
        }
    }

    private static NettyEventLoopFactory getNettyEventLoopFactory(int nThreads,
                                                                  ThreadFactory threadFactory,
                                                                  NettyTransportConfig config) {
        var ioHandlerFactory = getIoHandlerFactory(config);
        return new StandardEventLoopFactory(nThreads, threadFactory, ioHandlerFactory);
    }

    final class StandardEventLoopFactory implements NettyEventLoopFactory {
        private final int nThreads;
        private final ThreadFactory threadFactory;
        private final IoHandlerFactory ioHandlerFactory;

        StandardEventLoopFactory(int nThreads, ThreadFactory threadFactory, IoHandlerFactory ioHandlerFactory) {
            this.nThreads = nThreads;
            this.threadFactory = threadFactory;
            this.ioHandlerFactory = ioHandlerFactory;
        }

        @Override
        public EventLoopGroup build() {
            return new MultiThreadIoEventLoopGroup(nThreads, threadFactory, ioHandlerFactory);
        }
    }

    private static NettyChannelFactory getNioChannelFactory() {
        return NioNettyChannelFactory.INSTANCE;
    }

    private static NettyChannelFactory getEpollChannelFactory() {
        return EpollNettyChannelFactory.INSTANCE;
    }

    private static NettyChannelFactory getKQueueChannelFactory() {
        return KQueueNettyChannelFactory.INSTANCE;
    }

    private static NettyChannelFactory getUringChannelFactory() {
        return IoUringNettyChannelFactory.INSTANCE;
    }

    private static boolean isEpollAvailable() {
        return isClassPresent("io.netty.channel.epoll.Epoll") && Epoll.isAvailable();
    }

    private static boolean isKQueueAvailable() {
        return isClassPresent("io.netty.channel.kqueue.KQueue") && KQueue.isAvailable();
    }

    private static boolean isUringAvailable() {
        return isClassPresent("io.netty.channel.uring.IoUring") && IoUring.isAvailable();
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, NettyModule.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
