package io.koraframework.netty.common;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ServerChannel;

public interface NettyChannelFactory {

    default ChannelFactory<Channel> build() {
        return build(false);
    }

    ChannelFactory<Channel> build(boolean domainSocket);

    default ChannelFactory<ServerChannel> buildServer() {
        return buildServer(false);
    }

    ChannelFactory<ServerChannel> buildServer(boolean domainSocket);
}
