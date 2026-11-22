package io.koraframework.http.server.netty;

import io.koraframework.common.annotation.*;
import io.koraframework.netty.common.*;
import io.netty.buffer.*;

public interface NettyPublicHttpServerModule extends NettySystemHttpServerModule, NettyModule {

    @FactoryModule
    default NettyHttpServerFactoryModule nettyPublicHttpApi() {
        var allocator = new PooledByteBufAllocator(
            true,
            0,
            Runtime.getRuntime().availableProcessors(),
            8192,
            8,
            64,
            32,
            false
        );
        return new NettyHttpServerFactoryModule("kora-netty", "httpServer", allocator);
    }
}
