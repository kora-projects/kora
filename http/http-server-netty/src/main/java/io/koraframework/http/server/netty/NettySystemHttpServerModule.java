package io.koraframework.http.server.netty;

import io.koraframework.application.graph.*;
import io.koraframework.common.annotation.*;
import io.koraframework.http.server.common.system.*;
import io.koraframework.netty.common.*;
import io.netty.channel.*;

public interface NettySystemHttpServerModule extends SystemHttpServerModule, NettyModule {

    @FactoryModule
    @SystemApi
    default NettyHttpServerFactoryModule nettySystemHttpApi() {
        return new NettyHttpServerFactoryModule("kora-netty-system", "httpServer.system");
    }

    @DefaultComponent
    default Wrapped<NettyResources> nettyResources(
        @Tag(EventLoopBoss.class) LifecycleWrapper<EventLoopGroup> bossGroup,
        @Tag(EventLoopWorker.class) LifecycleWrapper<EventLoopGroup> workerGroup,
        NettyChannelFactory channelFactory
    ) {
        return new NettyResourcesWrapped(new NettyResources(bossGroup.value(), workerGroup.value(), channelFactory.serverChannelClass()));
    }

    final class NettyResourcesWrapped implements Wrapped<NettyResources> {
        private final NettyResources value;

        NettyResourcesWrapped(NettyResources value) {
            this.value = value;
        }

        @Override
        public NettyResources value() {
            return value;
        }
    }

    record NettyResources(
        EventLoopGroup bossGroup,
        EventLoopGroup workerGroup,
        Class<? extends ServerChannel> channelClass
    ) {}
}
