package io.koraframework.http.server.netty;

import io.koraframework.application.graph.*;
import io.koraframework.common.annotation.*;
import io.koraframework.config.common.*;
import io.koraframework.config.common.mapper.*;
import io.koraframework.http.server.common.system.*;
import io.koraframework.netty.common.*;
import io.netty.buffer.*;
import io.netty.channel.*;

public interface NettySystemHttpServerModule extends SystemHttpServerModule, NettyModule {

    @FactoryModule
    @SystemApi
    default NettyHttpServerFactoryModule nettySystemHttpApi() {
        var allocator = new PooledByteBufAllocator(
            true,
            0,
            1,
            4096,
            6,
            16,
            4,
            false
        );
        return new NettySystemHttpServerFactoryModule("kora-netty-system", "httpServer.system", allocator);
    }

    final class NettySystemHttpServerFactoryModule extends NettyHttpServerFactoryModule {
        NettySystemHttpServerFactoryModule(String name, String configPath, ByteBufAllocator allocator) {
            super(name, configPath, allocator);
        }

        @DefaultComponent
        @Tag(Tag.Factory.class)
        public KoraNettyServerConfig koraSystemNettyServerConfig(
            Config config,
            ConfigValueMapper<KoraSystemNettyServerConfig> mapper
        ) {
            return mapper.mapOrThrow(config.get(configPath));
        }
    }

    @DefaultComponent
    default Wrapped<NettyResources> nettyResources(
        @Tag(EventLoopBoss.class) LifecycleWrapper<EventLoopGroup> bossGroup,
        @Tag(EventLoopWorker.class) LifecycleWrapper<EventLoopGroup> workerGroup,
        NettyChannelFactory channelFactory
    ) {
        return new NettyResourcesWrapped(new NettyResources(bossGroup.value(), workerGroup.value(), channelFactory.buildServer()));
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
        ChannelFactory<ServerChannel> serverChannelFactory
    ) {}
}
