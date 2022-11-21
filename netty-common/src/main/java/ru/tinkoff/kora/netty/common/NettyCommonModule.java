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
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.LifecycleWrapper;
import ru.tinkoff.kora.common.Tag;

import javax.annotation.Nullable;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public interface NettyCommonModule {
    default LifecycleWrapper<EventLoopGroup> nettyEventLoopGroupLifecycle(@Tag(NettyCommonModule.class) @Nullable ThreadFactory threadFactory, @Tag(NettyCommonModule.class) @Nullable Integer size) {
        return new LifecycleWrapper<>(
            eventLoopGroup(threadFactory, size),
            elg -> Mono.empty(),
            // we don't have to wait because graph will shutdown loop after all the dependent components
            elg -> Mono.create(sink -> elg.shutdownGracefully(1, 1, TimeUnit.MILLISECONDS).addListener(future -> {
                if (future.isSuccess()) {
                    sink.success();
                } else {
                    sink.error(future.cause());
                }
            }))
        );
    }

    final class BossLoopGroup {}

    @Tag(BossLoopGroup.class)
    default LifecycleWrapper<EventLoopGroup> nettyEventBossLoopGroupLifecycle(@Tag(NettyCommonModule.class) @Nullable ThreadFactory threadFactory) {
        return new LifecycleWrapper<>(
            eventLoopGroup(threadFactory, 1),
            elg -> Mono.empty(),
            // we don't have to wait because graph will shutdown loop after all the dependent components
            elg -> Mono.create(sink -> elg.shutdownGracefully(1, 1, TimeUnit.MILLISECONDS).addListener(future -> {
                if (future.isSuccess()) {
                    sink.success();
                } else {
                    sink.error(future.cause());
                }
            }))
        );
    }

    private static EventLoopGroup eventLoopGroup(@Nullable ThreadFactory threadFactory, @Nullable Integer size) {
        if (size == null) {
            size = NettyRuntime.availableProcessors() * 2;
        }
        if (Epoll.isAvailable()) {
            return new EpollEventLoopGroup(size, threadFactory);
        }
        if (KQueue.isAvailable()) {
            return new KQueueEventLoopGroup(size, threadFactory);
        }
        return new NioEventLoopGroup(size, threadFactory);
    }

    static Class<? extends Channel> channelType() {
        if (Epoll.isAvailable()) {
            return EpollSocketChannel.class;
        }
        if (KQueue.isAvailable()) {
            return KQueueSocketChannel.class;
        }
        return NioSocketChannel.class;
    }

    static Class<? extends ServerChannel> serverChannelType() {
        if (Epoll.isAvailable()) {
            return EpollServerSocketChannel.class;
        }
        if (KQueue.isAvailable()) {
            return KQueueServerSocketChannel.class;
        }
        return NioServerSocketChannel.class;
    }
}
