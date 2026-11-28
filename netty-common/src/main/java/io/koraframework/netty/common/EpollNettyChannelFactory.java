package io.koraframework.netty.common;

import io.netty.channel.*;
import io.netty.channel.epoll.*;

final class EpollNettyChannelFactory implements NettyChannelFactory {
    static final EpollNettyChannelFactory INSTANCE = new EpollNettyChannelFactory();

    private EpollNettyChannelFactory() {
    }

    @Override
    public Class<? extends ServerChannel> serverChannelClass(boolean domainSocket) {
        return domainSocket ? EpollServerDomainSocketChannel.class : EpollServerSocketChannel.class;
    }

    @Override
    public ChannelFactory<Channel> build(boolean domainSocket) {
        return domainSocket ? EpollDomainSocketChannelFactory.INSTANCE : EpollSocketChannelFactory.INSTANCE;
    }

    @Override
    public ChannelFactory<ServerChannel> buildServer(boolean domainSocket) {
        return domainSocket ? EpollServerDomainSocketChannelFactory.INSTANCE : EpollServerSocketChannelFactory.INSTANCE;
    }

    private static final class EpollSocketChannelFactory implements ChannelFactory<Channel> {
        static final EpollSocketChannelFactory INSTANCE = new EpollSocketChannelFactory();

        @Override
        public Channel newChannel() {return new EpollSocketChannel();}
    }

    private static final class EpollDomainSocketChannelFactory implements ChannelFactory<Channel> {
        static final EpollDomainSocketChannelFactory INSTANCE = new EpollDomainSocketChannelFactory();

        @Override
        public Channel newChannel() {return new EpollDomainSocketChannel();}
    }

    private static final class EpollServerSocketChannelFactory implements ChannelFactory<ServerChannel> {
        static final EpollServerSocketChannelFactory INSTANCE = new EpollServerSocketChannelFactory();

        @Override
        public ServerChannel newChannel() {return new EpollServerSocketChannel();}
    }

    private static final class EpollServerDomainSocketChannelFactory implements ChannelFactory<ServerChannel> {
        static final EpollServerDomainSocketChannelFactory INSTANCE = new EpollServerDomainSocketChannelFactory();

        @Override
        public ServerChannel newChannel() {return new EpollServerDomainSocketChannel();}
    }
}
