package ru.tinkoff.kora.netty.common;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.*;
import io.netty.channel.kqueue.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.application.graph.LifecycleWrapper;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.netty.common.NettyTransportConfig.EventLoop;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public interface NettyCommonModule {

    final class BossLoopGroup {}

    final class WorkerLoopGroup {}

    default NettyTransportConfig nettyTransportConfig(Config config, ConfigValueExtractor<NettyTransportConfig> extractor) {
        return extractor.extract(config.get("netty"));
    }

    @Tag(WorkerLoopGroup.class)
    default LifecycleWrapper<EventLoopGroup> nettyEventLoopGroupLifecycle(@Tag(NettyCommonModule.class) @Nullable ThreadFactory threadFactory,
                                                                          NettyTransportConfig nettyTransportConfig) {
        return new LifecycleWrapper<>(
            eventLoopGroup(threadFactory, nettyTransportConfig.threads(), nettyTransportConfig.transport()),
            elg -> {},
            // we don't have to wait because graph will shutdown loop after all the dependent components
            elg -> elg.shutdownGracefully(1, 1, TimeUnit.MILLISECONDS).get()
        );
    }

    @Tag(BossLoopGroup.class)
    default LifecycleWrapper<EventLoopGroup> nettyEventBossLoopGroupLifecycle(@Tag(NettyCommonModule.class) @Nullable ThreadFactory threadFactory,
                                                                              NettyTransportConfig nettyTransportConfig) {
        return new LifecycleWrapper<>(
            eventLoopGroup(threadFactory, 1, nettyTransportConfig.transport()),
            elg -> {},
            // we don't have to wait because graph will shutdown loop after all the dependent components
            elg -> {
                elg.shutdownGracefully(1, 1, TimeUnit.MILLISECONDS).get();
            }
        );
    }

    private static EventLoopGroup eventLoopGroup(@Nullable ThreadFactory threadFactory,
                                                 Integer size,
                                                 @Nullable EventLoop preferredEventLoop) {
        if (preferredEventLoop != null) {
            if (EventLoop.EPOLL == preferredEventLoop && isEpollAvailable()) {
                return new EpollEventLoopGroup(size, threadFactory);
            } else if (EventLoop.KQUEUE == preferredEventLoop && isKQueueAvailable()) {
                return new KQueueEventLoopGroup(size, threadFactory);
            } else if (EventLoop.NIO == preferredEventLoop) {
                return new NioEventLoopGroup(size, threadFactory);
            }
        }

        // default behavior
        if (isEpollAvailable()) {
            return new EpollEventLoopGroup(size, threadFactory);
        } else if (isKQueueAvailable()) {
            return new KQueueEventLoopGroup(size, threadFactory);
        } else {
            return new NioEventLoopGroup(size, threadFactory);
        }
    }

    default NettyChannelFactory nettyChannelFactory(NettyTransportConfig nettyConfig) {
        if (nettyConfig.transport() != null) {
            if (EventLoop.EPOLL == nettyConfig.transport() && isEpollAvailable()) {
                return getEpollChannelFactory();
            } else if (EventLoop.KQUEUE == nettyConfig.transport() && isKQueueAvailable()) {
                return getKQueueChannelFactory();
            } else if (EventLoop.NIO == nettyConfig.transport()) {
                return getNioChannelFactory();
            }
        }

        // default behavior
        if (isEpollAvailable()) {
            return getEpollChannelFactory();
        } else if (isKQueueAvailable()) {
            return getKQueueChannelFactory();
        } else {
            return getNioChannelFactory();
        }
    }

    private static NettyChannelFactory getNioChannelFactory() {
        return new NettyChannelFactory() {
            @Override
            public ChannelFactory<Channel> getClientFactory(boolean domainSocket) {
                    //TODO Netty 4.1.110+
//                if (domainSocket) {
//                    return NioDomainSocketChannel::new;
//                } else {
                    return NioSocketChannel::new;
//                }
            }

            @Override
            public ChannelFactory<ServerChannel> getServerFactory(boolean domainSocket) {
                //TODO Netty 4.1.110+
//                if (domainSocket) {
//                    return NioServerDomainSocketChannel::new;
//                } else {
                    return NioServerSocketChannel::new;
//                }
            }
        };
    }

    private static NettyChannelFactory getEpollChannelFactory() {
        return new NettyChannelFactory() {
            @Override
            public ChannelFactory<Channel> getClientFactory(boolean domainSocket) {
                if (domainSocket) {
                    return EpollDomainSocketChannel::new;
                } else {
                    return EpollSocketChannel::new;
                }
            }

            @Override
            public ChannelFactory<ServerChannel> getServerFactory(boolean domainSocket) {
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
            public ChannelFactory<Channel> getClientFactory(boolean domainSocket) {
                if (domainSocket) {
                    return KQueueDomainSocketChannel::new;
                } else {
                    return KQueueSocketChannel::new;
                }
            }

            @Override
            public ChannelFactory<ServerChannel> getServerFactory(boolean domainSocket) {
                if (domainSocket) {
                    return KQueueServerDomainSocketChannel::new;
                } else {
                    return KQueueServerSocketChannel::new;
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

    private static boolean isClassPresent(String className) {
        try {
            return NettyCommonModule.class.getClassLoader().loadClass(className) != null;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
