package io.koraframework.netty.common;

import io.koraframework.application.graph.LifecycleWrapper;
import io.koraframework.common.DefaultComponent;
import io.koraframework.common.Tag;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractor;
import io.koraframework.netty.common.NettyTransportConfig.EventLoopType;
import io.netty.channel.*;
import io.netty.channel.epoll.*;
import io.netty.channel.kqueue.*;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.nio.NioDomainSocketChannel;
import io.netty.channel.socket.nio.NioServerDomainSocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.uring.*;
import io.netty.util.concurrent.DefaultThreadFactory;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public interface NettyModule {

    final class EventLoopBoss {
        private EventLoopBoss() {}
    }

    final class EventLoopWorker {
        private EventLoopWorker() {}
    }

    default NettyTransportConfig nettyTransportConfig(Config config, ConfigValueExtractor<NettyTransportConfig> extractor) {
        return extractor.extract(config.get("netty"));
    }

    @Tag(EventLoopBoss.class)
    @DefaultComponent
    default NettyEventLoopFactory nettyEventLoopBossFactory(@Tag(EventLoopBoss.class) @Nullable ThreadFactory threadFactory,
                                                            NettyTransportConfig config) {
        var tf = (threadFactory == null)
            ? new DefaultThreadFactory("netty-kora-boss", true)
            : threadFactory;
        return getNettyEventLoopFactory(tf, config);
    }

    @Tag(EventLoopBoss.class)
    @DefaultComponent
    default LifecycleWrapper<EventLoopGroup> nettyEventLoopBossGroup(@Tag(EventLoopBoss.class) NettyEventLoopFactory nettyEventLoopFactory) {
        return new LifecycleWrapper<>(
            nettyEventLoopFactory.build(),
            elg -> {},
            // we don't have to wait because graph will shutdown loop after all the dependent components
            elg -> {
                elg.shutdownGracefully(1, 1, TimeUnit.MILLISECONDS).get();
            }
        );
    }

    @Tag(EventLoopWorker.class)
    @DefaultComponent
    default NettyEventLoopFactory nettyEventLoopWorkerFactory(@Tag(EventLoopWorker.class) @Nullable ThreadFactory threadFactory,
                                                              NettyTransportConfig config) {
        var tf = (threadFactory == null)
            ? new DefaultThreadFactory("netty-kora-worker", true)
            : threadFactory;
        return getNettyEventLoopFactory(tf, config);
    }

    @Tag(EventLoopWorker.class)
    @DefaultComponent
    default LifecycleWrapper<EventLoopGroup> nettyEventLoopWorkerGroup(@Tag(EventLoopWorker.class) NettyEventLoopFactory nettyEventLoopFactory) {
        return new LifecycleWrapper<>(
            nettyEventLoopFactory.build(),
            elg -> {},
            // we don't have to wait because graph will shutdown loop after all the dependent components
            elg -> elg.shutdownGracefully(1, 1, TimeUnit.MILLISECONDS).get()
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

    private static NettyEventLoopFactory getNettyEventLoopFactory(ThreadFactory threadFactory,
                                                                  NettyTransportConfig config) {
        int nThreads = config.threads();
        var transport = config.transport();
        if (transport != null) {
            if (transport == EventLoopType.URING && isUringAvailable()) {
                return () -> new MultiThreadIoEventLoopGroup(nThreads, threadFactory, IoUringIoHandler.newFactory());
            } else if (transport == EventLoopType.EPOLL && isEpollAvailable()) {
                return () -> new MultiThreadIoEventLoopGroup(nThreads, threadFactory, EpollIoHandler.newFactory());
            } else if (transport == EventLoopType.KQUEUE && isKQueueAvailable()) {
                return () -> new MultiThreadIoEventLoopGroup(nThreads, threadFactory, KQueueIoHandler.newFactory());
            } else if (transport == EventLoopType.NIO) {
                return () -> new MultiThreadIoEventLoopGroup(nThreads, threadFactory, NioIoHandler.newFactory());
            }
        }

        // default behavior
        if (isUringAvailable()) {
            return () -> new MultiThreadIoEventLoopGroup(nThreads, threadFactory, IoUringIoHandler.newFactory());
        } else if (isEpollAvailable()) {
            return () -> new MultiThreadIoEventLoopGroup(nThreads, threadFactory, EpollIoHandler.newFactory());
        } else if (isKQueueAvailable()) {
            return () -> new MultiThreadIoEventLoopGroup(nThreads, threadFactory, KQueueIoHandler.newFactory());
        } else {
            return () -> new MultiThreadIoEventLoopGroup(nThreads, threadFactory, NioIoHandler.newFactory());
        }
    }

    private static NettyChannelFactory getNioChannelFactory() {
        return new NettyChannelFactory() {
            @Override
            public ChannelFactory<Channel> build(boolean domainSocket) {
                if (domainSocket) {
                    return NioDomainSocketChannel::new;
                } else {
                    return NioSocketChannel::new;
                }
            }

            @Override
            public ChannelFactory<ServerChannel> buildServer(boolean domainSocket) {
                if (domainSocket) {
                    return NioServerDomainSocketChannel::new;
                } else {
                    return NioServerSocketChannel::new;
                }
            }
        };
    }

    private static NettyChannelFactory getEpollChannelFactory() {
        return new NettyChannelFactory() {
            @Override
            public ChannelFactory<Channel> build(boolean domainSocket) {
                if (domainSocket) {
                    return EpollDomainSocketChannel::new;
                } else {
                    return EpollSocketChannel::new;
                }
            }

            @Override
            public ChannelFactory<ServerChannel> buildServer(boolean domainSocket) {
                if (domainSocket) {
                    return EpollServerDomainSocketChannel::new;
                } else {
                    return EpollServerSocketChannel::new;
                }
            }
        };
    }

    private static NettyChannelFactory getKQueueChannelFactory() {
        return new NettyChannelFactory() {
            @Override
            public ChannelFactory<Channel> build(boolean domainSocket) {
                if (domainSocket) {
                    return KQueueDomainSocketChannel::new;
                } else {
                    return KQueueSocketChannel::new;
                }
            }

            @Override
            public ChannelFactory<ServerChannel> buildServer(boolean domainSocket) {
                if (domainSocket) {
                    return KQueueServerDomainSocketChannel::new;
                } else {
                    return KQueueServerSocketChannel::new;
                }
            }
        };
    }

    private static NettyChannelFactory getUringChannelFactory() {
        return new NettyChannelFactory() {
            @Override
            public ChannelFactory<Channel> build(boolean domainSocket) {
                if (domainSocket) {
                    return IoUringDomainSocketChannel::new;
                } else {
                    return IoUringSocketChannel::new;
                }
            }

            @Override
            public ChannelFactory<ServerChannel> buildServer(boolean domainSocket) {
                if (domainSocket) {
                    return IoUringServerDomainSocketChannel::new;
                } else {
                    return IoUringServerSocketChannel::new;
                }
            }
        };
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
            return NettyModule.class.getClassLoader().loadClass(className) != null;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
