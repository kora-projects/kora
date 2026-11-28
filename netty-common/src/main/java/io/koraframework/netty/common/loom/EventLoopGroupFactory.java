package io.koraframework.netty.common.loom;

import io.netty.channel.*;

import java.util.concurrent.*;

public final class EventLoopGroupFactory {

    private final LoomCarrierConfig loomConfig;
    private final EventLoopLoomFactory eventLoopLoomFactory;

    public EventLoopGroupFactory(LoomCarrierConfig loomConfig, EventLoopLoomFactory eventLoopLoomFactory) {
        this.loomConfig = loomConfig;
        this.eventLoopLoomFactory = eventLoopLoomFactory;
    }

    public EventLoopGroup createEventLoopGroup(int nThreads, String namePrefix, boolean isLoomCarrier, IoHandlerFactory ioHandlerFactory) {
        var threadFactory = Thread.ofPlatform()
            .name(namePrefix + "-", 0)
            .daemon(true)
            .stackSize(256 * 1024)
            .factory();
        var executor = new ThreadFactoryExecutor(threadFactory);

        if (isLoomCarrier) {
            PrivateLoomSupport.isCarrierThreadSupportedCheck();
            var carrierFactory = new LoomCarrierGroup.Factory(eventLoopLoomFactory, loomConfig);
            return carrierFactory.create(nThreads, executor, ioHandlerFactory);
        } else {
            return new MultiThreadIoEventLoopGroup(nThreads, executor, ioHandlerFactory);
        }
    }

    private static final class ThreadFactoryExecutor implements Executor {
        private final ThreadFactory threadFactory;

        private ThreadFactoryExecutor(ThreadFactory threadFactory) {
            this.threadFactory = threadFactory;
        }

        @Override
        public void execute(Runnable command) {
            threadFactory.newThread(command).start();
        }
    }
}
