package ru.tinkoff.kora.vertx.common;

import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.*;
import io.netty.channel.kqueue.*;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.InternetProtocolFamily;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.unix.DomainSocketAddress;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.SucceededFuture;
import io.vertx.core.datagram.DatagramSocketOptions;
import io.vertx.core.net.ClientOptionsBase;
import io.vertx.core.net.NetServerOptions;
import io.vertx.core.net.impl.SocketAddressImpl;
import io.vertx.core.net.impl.transport.Transport;

import javax.annotation.Nonnull;
import java.net.SocketAddress;
import java.util.Iterator;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class VertxEventLoopGroupTransport extends Transport {
    private final TransportType type;
    private final EventLoopGroup eventLoopGroup;

    public enum TransportType {
        NIO, EPOLL, KQUEUE
    }

    public VertxEventLoopGroupTransport(EventLoopGroup eventLoopGroup) {
        this.eventLoopGroup = eventLoopGroup;
        if (this.eventLoopGroup.getClass().getName().contains("Epoll")) {
            this.type = TransportType.EPOLL;
        } else if (this.eventLoopGroup.getClass().getName().contains("KQueue")) {
            this.type = TransportType.KQUEUE;
        } else {
            this.type = TransportType.NIO;
        }
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public Throwable unavailabilityCause() {
        return null;
    }

    @Override
    public SocketAddress convert(io.vertx.core.net.SocketAddress address) {
        return switch (this.type) {
            case NIO -> super.convert(address);
            case EPOLL, KQUEUE -> {
                if (address.isDomainSocket()) {
                    yield new DomainSocketAddress(address.path());
                } else {
                    yield super.convert(address);
                }
            }
        };
    }

    @Override
    public io.vertx.core.net.SocketAddress convert(SocketAddress address) {
        return switch (this.type) {
            case NIO -> super.convert(address);
            case EPOLL, KQUEUE -> {
                if (address instanceof DomainSocketAddress) {
                    yield new SocketAddressImpl(((DomainSocketAddress) address).path());
                }
                yield super.convert(address);
            }
        };
    }

    @Override
    public EventLoopGroup eventLoopGroup(int type, int nThreads, ThreadFactory threadFactory, int ioRatio) {
        return new AbstractEventLoopGroup() {
            private final EventLoopGroup eventLoopGroup = VertxEventLoopGroupTransport.this.eventLoopGroup;

            @Override
            public EventLoop next() {
                return this.eventLoopGroup.next();
            }

            @Override
            public ChannelFuture register(Channel channel) {
                return this.eventLoopGroup.register(channel);
            }

            @Override
            public ChannelFuture register(ChannelPromise promise) {
                return this.eventLoopGroup.register(promise);
            }

            @Override
            public ChannelFuture register(Channel channel, ChannelPromise promise) {
                return this.eventLoopGroup.register(channel, promise);
            }

            @Override
            public void shutdown() {

            }

            @Override
            public boolean isShuttingDown() {
                return false;
            }

            @Override
            public Future<?> shutdownGracefully(long quietPeriod, long timeout, TimeUnit unit) {
                return new SucceededFuture<>(this.eventLoopGroup.next(), null);
            }

            @Override
            public Future<?> terminationFuture() {
                return null;
            }

            @Override
            public Iterator<EventExecutor> iterator() {
                return this.eventLoopGroup.iterator();
            }

            @Override
            public boolean isShutdown() {
                return false;
            }

            @Override
            public boolean isTerminated() {
                return false;
            }

            @Override
            public boolean awaitTermination(long timeout, @Nonnull TimeUnit unit) throws InterruptedException {
                return false;
            }
        };
    }

    @Override
    public DatagramChannel datagramChannel() {
        return switch (this.type) {
            case NIO -> new NioDatagramChannel();
            case EPOLL -> new EpollDatagramChannel();
            case KQUEUE -> new KQueueDatagramChannel();
        };
    }

    @Override
    public DatagramChannel datagramChannel(InternetProtocolFamily family) {
        return switch (this.type) {
            case NIO -> super.datagramChannel(family);
            case EPOLL -> new EpollDatagramChannel();
            case KQUEUE -> new KQueueDatagramChannel();
        };
    }

    @Override
    public ChannelFactory<? extends Channel> channelFactory(boolean domainSocket) {
        return switch (this.type) {
            case NIO -> {
                if (domainSocket) {
                    throw new IllegalArgumentException();
                }
                yield NioSocketChannel::new;
            }
            case EPOLL -> {
                if (domainSocket) {
                    yield EpollDomainSocketChannel::new;
                } else {
                    yield EpollSocketChannel::new;
                }
            }
            case KQUEUE -> {
                if (domainSocket) {
                    yield KQueueDomainSocketChannel::new;
                } else {
                    yield KQueueSocketChannel::new;
                }
            }
        };
    }

    @Override
    public ChannelFactory<? extends ServerChannel> serverChannelFactory(boolean domainSocket) {
        return switch (this.type) {
            case NIO -> {
                if (domainSocket) {
                    throw new IllegalArgumentException();
                }
                yield NioServerSocketChannel::new;
            }
            case EPOLL -> {
                if (domainSocket) {
                    yield EpollServerDomainSocketChannel::new;
                } else {
                    yield EpollServerSocketChannel::new;
                }
            }
            case KQUEUE -> {
                if (domainSocket) {
                    yield KQueueServerDomainSocketChannel::new;
                } else {
                    yield KQueueServerSocketChannel::new;
                }
            }
        };
    }

    @Override
    public void configure(DatagramChannel channel, DatagramSocketOptions options) {
        switch (this.type) {
            case EPOLL, KQUEUE -> channel.config().setOption(EpollChannelOption.SO_REUSEPORT, options.isReusePort());
            default -> {}
        }
        super.configure(channel, options);
    }

    @Override
    public void configure(ClientOptionsBase options, boolean domainSocket, Bootstrap bootstrap) {
        if (this.type == TransportType.EPOLL) {
            if (!domainSocket) {
                if (options.isTcpFastOpen()) {
                    bootstrap.option(EpollChannelOption.TCP_FASTOPEN_CONNECT, options.isTcpFastOpen());
                }
                bootstrap.option(EpollChannelOption.TCP_QUICKACK, options.isTcpQuickAck());
                bootstrap.option(EpollChannelOption.TCP_CORK, options.isTcpCork());
            }
        }
        super.configure(options, domainSocket, bootstrap);
    }

    @Override
    public void configure(NetServerOptions options, boolean domainSocket, ServerBootstrap bootstrap) {
        if (this.type == TransportType.EPOLL) {
            if (!domainSocket) {
                bootstrap.option(EpollChannelOption.SO_REUSEPORT, options.isReusePort());
                if (options.isTcpFastOpen()) {
                    bootstrap.option(EpollChannelOption.TCP_FASTOPEN, options.isTcpFastOpen() ? 256 : 0);
                }
                bootstrap.childOption(EpollChannelOption.TCP_QUICKACK, options.isTcpQuickAck());
                bootstrap.childOption(EpollChannelOption.TCP_CORK, options.isTcpCork());
            }
        }
        if (this.type == TransportType.KQUEUE) {
            if (!domainSocket) {
                bootstrap.option(KQueueChannelOption.SO_REUSEPORT, options.isReusePort());
            }
        }
        super.configure(options, domainSocket, bootstrap);
    }
}
