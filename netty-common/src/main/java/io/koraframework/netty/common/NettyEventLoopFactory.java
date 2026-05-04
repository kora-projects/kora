package io.koraframework.netty.common;

import io.netty.channel.EventLoopGroup;

public interface NettyEventLoopFactory {

    EventLoopGroup build();
}
