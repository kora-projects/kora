package io.koraframework.netty.common;

import io.netty.channel.*;

public interface NettyChannelFactory {

    default ChannelFactory<Channel> build() {
        return build(false);
    }

    ChannelFactory<Channel> build(boolean domainSocket);

    default ChannelFactory<ServerChannel> buildServer() {
        return buildServer(false);
    }

    ChannelFactory<ServerChannel> buildServer(boolean domainSocket);

    default Class<? extends ServerChannel> serverChannelClass() {
        return serverChannelClass(false);
    }

    default Class<? extends ServerChannel> serverChannelClass(boolean domainSocket) {
        var ch = buildServer(domainSocket).newChannel();
        try {
            return ch.getClass();
        } finally {
            ch.close();
        }
    }
}
