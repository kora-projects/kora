package ru.tinkoff.kora.netty.common;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ServerChannel;

public interface NettyChannelFactory {

    default ChannelFactory<Channel> getClientFactory() {
        return getClientFactory(false);
    }

    ChannelFactory<Channel> getClientFactory(boolean domainSocket);

    default ChannelFactory<ServerChannel> getServerFactory() {
        return getServerFactory(false);
    }

    ChannelFactory<ServerChannel> getServerFactory(boolean domainSocket);
}
