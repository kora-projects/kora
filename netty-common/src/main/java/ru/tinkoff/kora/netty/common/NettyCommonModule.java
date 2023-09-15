package ru.tinkoff.kora.netty.common;

import io.netty.channel.Channel;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.NettyRuntime;
import ru.tinkoff.kora.application.graph.LifecycleWrapper;
import ru.tinkoff.kora.common.Tag;

import javax.annotation.Nullable;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public interface NettyCommonModule {

    default LifecycleWrapper<EventLoopGroup> nettyEventLoopGroupLifecycle(@Tag(NettyCommonModule.class) @Nullable ThreadFactory threadFactory, @Tag(NettyCommonModule.class) @Nullable Integer size) {
        return new LifecycleWrapper<>(
            eventLoopGroup(threadFactory, size),
            elg -> {},
            // we don't have to wait because graph will shutdown loop after all the dependent components
            elg -> elg.shutdownGracefully(1, 1, TimeUnit.MILLISECONDS).get()
        );
    }

    final class BossLoopGroup {}

    @Tag(BossLoopGroup.class)
    default LifecycleWrapper<EventLoopGroup> nettyEventBossLoopGroupLifecycle(@Tag(NettyCommonModule.class) @Nullable ThreadFactory threadFactory) {
        return new LifecycleWrapper<>(
            eventLoopGroup(threadFactory, 1),
            elg -> {},
            // we don't have to wait because graph will shutdown loop after all the dependent components
            elg -> {
                elg.shutdownGracefully(1, 1, TimeUnit.MILLISECONDS).get();
            }
        );
    }

    private static EventLoopGroup eventLoopGroup(@Nullable ThreadFactory threadFactory, @Nullable Integer size) {
        if (size == null) {
            size = NettyRuntime.availableProcessors() * 2;
        }

        if (isClassPresent("io.netty.channel.epoll.Epoll") && Epoll.isAvailable()) {
            return new EpollEventLoopGroup(size, threadFactory);
        } else if (isClassPresent("io.netty.channel.kqueue.KQueue") && KQueue.isAvailable()) {
            return new KQueueEventLoopGroup(size, threadFactory);
        } else {
            return new NioEventLoopGroup(size, threadFactory);
        }
    }

    private static boolean isClassPresent(String className) {
        try {
            return NettyCommonModule.class.getClassLoader().loadClass(className) != null;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    static Class<? extends Channel> channelType() {
        if (isClassPresent("io.netty.channel.epoll.Epoll") && Epoll.isAvailable()) {
            return EpollSocketChannel.class;
        } else if (isClassPresent("io.netty.channel.kqueue.KQueue") && KQueue.isAvailable()) {
            return KQueueSocketChannel.class;
        } else {
            return NioSocketChannel.class;
        }
    }

    static Class<? extends ServerChannel> serverChannelType() {
        if (isClassPresent("io.netty.channel.epoll.Epoll") && Epoll.isAvailable()) {
            return EpollServerSocketChannel.class;
        } else if (isClassPresent("io.netty.channel.kqueue.KQueue") && KQueue.isAvailable()) {
            return KQueueServerSocketChannel.class;
        } else {
            return NioServerSocketChannel.class;
        }
    }
}
