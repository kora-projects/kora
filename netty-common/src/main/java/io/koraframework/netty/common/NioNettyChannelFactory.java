package io.koraframework.netty.common;

import io.netty.channel.*;
import io.netty.channel.socket.nio.*;

final class NioNettyChannelFactory implements NettyChannelFactory {
    static final NioNettyChannelFactory INSTANCE = new NioNettyChannelFactory();

    private NioNettyChannelFactory() {
    }

    @Override
    public Class<? extends ServerChannel> serverChannelClass(boolean domainSocket) {
        return domainSocket ? NioServerDomainSocketChannel.class : NioServerSocketChannel.class;
    }

    @Override
    public ChannelFactory<Channel> build(boolean domainSocket) {
        return domainSocket ? NioDomainSocketChannelFactory.INSTANCE : NioSocketChannelFactory.INSTANCE;
    }

    @Override
    public ChannelFactory<ServerChannel> buildServer(boolean domainSocket) {
        return domainSocket ? NioServerDomainSocketChannelFactory.INSTANCE : NioServerSocketChannelFactory.INSTANCE;
    }

    private static final class NioSocketChannelFactory implements ChannelFactory<Channel> {
        static final NioSocketChannelFactory INSTANCE = new NioSocketChannelFactory();

        @Override
        public Channel newChannel() {return new NioSocketChannel();}
    }

    private static final class NioDomainSocketChannelFactory implements ChannelFactory<Channel> {
        static final NioDomainSocketChannelFactory INSTANCE = new NioDomainSocketChannelFactory();

        @Override
        public Channel newChannel() {return new NioDomainSocketChannel();}
    }

    private static final class NioServerSocketChannelFactory implements ChannelFactory<ServerChannel> {
        static final NioServerSocketChannelFactory INSTANCE = new NioServerSocketChannelFactory();

        @Override
        public ServerChannel newChannel() {return new NioServerSocketChannel();}
    }

    private static final class NioServerDomainSocketChannelFactory implements ChannelFactory<ServerChannel> {
        static final NioServerDomainSocketChannelFactory INSTANCE = new NioServerDomainSocketChannelFactory();

        @Override
        public ServerChannel newChannel() {return new NioServerDomainSocketChannel();}
    }
}
