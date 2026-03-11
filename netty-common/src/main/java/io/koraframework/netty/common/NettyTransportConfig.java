package io.koraframework.netty.common;

import io.netty.util.NettyRuntime;
import org.jspecify.annotations.Nullable;
import io.koraframework.config.common.annotation.ConfigValueExtractor;

@ConfigValueExtractor
public interface NettyTransportConfig {

    enum EventLoop {
        NIO,
        EPOLL,
        KQUEUE
    }

    /**
     * @return Preferred Netty transport to use, if not available then first available is used by order (EPOLL / KQUEUE / NIO)
     */
    @Nullable
    EventLoop transport();

    default int threads() {
        return NettyRuntime.availableProcessors() * 2;
    }
}
