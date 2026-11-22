package io.koraframework.netty.common;

import io.netty.channel.*;
import io.netty.channel.uring.*;

final class IoUringNettyChannelFactory implements NettyChannelFactory {
    static final IoUringNettyChannelFactory INSTANCE = new IoUringNettyChannelFactory();

    private IoUringNettyChannelFactory() {
    }

    @Override
    public Class<? extends ServerChannel> serverChannelClass(boolean domainSocket) {
        return domainSocket ? IoUringServerDomainSocketChannel.class : IoUringServerSocketChannel.class;
    }

    @Override
    public ChannelFactory<Channel> build(boolean domainSocket) {
        return domainSocket ? IoUringDomainSocketChannelFactory.INSTANCE : IoUringSocketChannelFactory.INSTANCE;
    }

    @Override
    public ChannelFactory<ServerChannel> buildServer(boolean domainSocket) {
        return domainSocket ? IoUringServerDomainSocketChannelFactory.INSTANCE : IoUringServerSocketChannelFactory.INSTANCE;
    }

    private static final class IoUringSocketChannelFactory implements ChannelFactory<Channel> {
        static final IoUringSocketChannelFactory INSTANCE = new IoUringSocketChannelFactory();

        @Override
        public Channel newChannel() {return new IoUringSocketChannel();}
    }

    private static final class IoUringDomainSocketChannelFactory implements ChannelFactory<Channel> {
        static final IoUringDomainSocketChannelFactory INSTANCE = new IoUringDomainSocketChannelFactory();

        @Override
        public Channel newChannel() {return new IoUringDomainSocketChannel();}
    }

    private static final class IoUringServerSocketChannelFactory implements ChannelFactory<ServerChannel> {
        static final IoUringServerSocketChannelFactory INSTANCE = new IoUringServerSocketChannelFactory();

        @Override
        public ServerChannel newChannel() {return new IoUringServerSocketChannel();}
    }

    private static final class IoUringServerDomainSocketChannelFactory implements ChannelFactory<ServerChannel> {
        static final IoUringServerDomainSocketChannelFactory INSTANCE = new IoUringServerDomainSocketChannelFactory();

        @Override
        public ServerChannel newChannel() {return new IoUringServerDomainSocketChannel();}
    }
}
