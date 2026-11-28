package io.koraframework.netty.common;

import io.netty.channel.*;
import io.netty.channel.kqueue.*;

final class KQueueNettyChannelFactory implements NettyChannelFactory {
    static final KQueueNettyChannelFactory INSTANCE = new KQueueNettyChannelFactory();

    private KQueueNettyChannelFactory() {
    }

    @Override
    public Class<? extends ServerChannel> serverChannelClass(boolean domainSocket) {
        return domainSocket ? KQueueServerDomainSocketChannel.class : KQueueServerSocketChannel.class;
    }

    @Override
    public ChannelFactory<Channel> build(boolean domainSocket) {
        return domainSocket ? KQueueDomainSocketChannelFactory.INSTANCE : KQueueSocketChannelFactory.INSTANCE;
    }

    @Override
    public ChannelFactory<ServerChannel> buildServer(boolean domainSocket) {
        return domainSocket ? KQueueServerDomainSocketChannelFactory.INSTANCE : KQueueServerSocketChannelFactory.INSTANCE;
    }

    private static final class KQueueSocketChannelFactory implements ChannelFactory<Channel> {
        static final KQueueSocketChannelFactory INSTANCE = new KQueueSocketChannelFactory();

        @Override
        public Channel newChannel() {return new KQueueSocketChannel();}
    }

    private static final class KQueueDomainSocketChannelFactory implements ChannelFactory<Channel> {
        static final KQueueDomainSocketChannelFactory INSTANCE = new KQueueDomainSocketChannelFactory();

        @Override
        public Channel newChannel() {return new KQueueDomainSocketChannel();}
    }

    private static final class KQueueServerSocketChannelFactory implements ChannelFactory<ServerChannel> {
        static final KQueueServerSocketChannelFactory INSTANCE = new KQueueServerSocketChannelFactory();

        @Override
        public ServerChannel newChannel() {return new KQueueServerSocketChannel();}
    }

    private static final class KQueueServerDomainSocketChannelFactory implements ChannelFactory<ServerChannel> {
        static final KQueueServerDomainSocketChannelFactory INSTANCE = new KQueueServerDomainSocketChannelFactory();

        @Override
        public ServerChannel newChannel() {return new KQueueServerDomainSocketChannel();}
    }
}
