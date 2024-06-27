package ru.tinkoff.kora.netty.common;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ServerChannel;

public interface NettyChannelFactory {

    ChannelFactory<Channel> getClientFactory();

    ChannelFactory<ServerChannel> getServerFactory();
}
