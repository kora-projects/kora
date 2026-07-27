package ru.tinkoff.kora.netty.common;

import io.netty.util.NettyRuntime;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

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

    /**
     * @return Number of worker event loop threads that process connections and network I/O, server boss event loop is not affected.
     */
    default int threads() {
        return NettyRuntime.availableProcessors() * 2;
    }
}
