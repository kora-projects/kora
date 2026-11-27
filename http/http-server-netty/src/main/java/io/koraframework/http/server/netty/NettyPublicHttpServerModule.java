package io.koraframework.http.server.netty;

import io.koraframework.common.annotation.*;
import io.koraframework.netty.common.*;
import io.netty.buffer.*;

public interface NettyPublicHttpServerModule extends NettySystemHttpServerModule, NettyModule {

    boolean NETTY_ALLOCATOR_INITIALIZED = initNettyDefaults();

    private static boolean initNettyDefaults() {
        if (System.getProperty("io.netty.allocator.type") == null) {
            System.setProperty("io.netty.allocator.type", "pooled");
        }
        if (System.getProperty("io.netty.leakDetection.level") == null) {
            System.setProperty("io.netty.leakDetection.level", "disabled");
        }
        return true;
    }

    @FactoryModule
    default NettyHttpServerFactoryModule nettyPublicHttpApi() {
        var allocator = new PooledByteBufAllocator(
            true,
            0,
            Runtime.getRuntime().availableProcessors(),
            8192,
            8,
            128,
            32,
            false
        );
        return new NettyHttpServerFactoryModule("kora-netty", "httpServer", allocator);
    }
}
