package io.koraframework.netty.common.loom;

import io.koraframework.netty.common.*;
import io.netty.channel.*;

public final class LoomEventLoopFactory implements NettyEventLoopFactory {
    private final EventLoopGroupFactory eventLoopGroupFactory;
    private final int nThreads;
    private final String namePrefix;
    private final boolean isLoomCarrier;
    private final IoHandlerFactory ioHandlerFactory;

    public LoomEventLoopFactory(
        EventLoopGroupFactory eventLoopGroupFactory,
        int nThreads,
        String namePrefix,
        boolean isLoomCarrier,
        IoHandlerFactory ioHandlerFactory) {
        this.eventLoopGroupFactory = eventLoopGroupFactory;
        this.nThreads = nThreads;
        this.namePrefix = namePrefix;
        this.isLoomCarrier = isLoomCarrier;
        this.ioHandlerFactory = ioHandlerFactory;
    }

    @Override
    public EventLoopGroup build() {
        return eventLoopGroupFactory.createEventLoopGroup(nThreads, namePrefix, isLoomCarrier, ioHandlerFactory);
    }
}
