package io.koraframework.netty.common.loom;

import io.netty.util.concurrent.*;

import java.util.concurrent.*;

public final class EventLoopLoomFactory {

    public final FastThreadLocal<ThreadFactory> targetScheduler = new FastThreadLocal<>();
    private final boolean isLoomCarrierEnabled;

    public EventLoopLoomFactory(boolean isLoomCarrierEnabled) {
        this.isLoomCarrierEnabled = isLoomCarrierEnabled;
    }

    public ThreadFactory createVirtualThreadFactory(String namePrefix, boolean isVirtualExecutor, boolean anyLoopIsLoomCarrier) {
        if (!isVirtualExecutor) {
            throw new IllegalStateException("Virtual executor should be virtual");
        }

        ThreadFactory delegate = Thread.ofVirtual()
            .inheritInheritableThreadLocals(false)
            .name(namePrefix, 1L)
            .factory();
        if (!isLoomCarrierEnabled || !anyLoopIsLoomCarrier) {
            // carrier scheduler is disabled, fallback to default ForkJoinPool virtual thread factory
            return delegate;
        }

        return new CarrierAwareThreadFactory(targetScheduler, delegate);
    }

    private static final class CarrierAwareThreadFactory implements ThreadFactory {
        private final FastThreadLocal<ThreadFactory> targetScheduler;
        private final ThreadFactory delegate;

        private CarrierAwareThreadFactory(FastThreadLocal<ThreadFactory> targetScheduler, ThreadFactory delegate) {
            this.targetScheduler = targetScheduler;
            this.delegate = delegate;
        }

        @Override
        public Thread newThread(Runnable r) {
            // getIfExists avoids allocating a thread local map for every thread that creates a
            // virtual thread and is not an event loop
            ThreadFactory currentTarget = targetScheduler.getIfExists();
            if (currentTarget == null) {
                return delegate.newThread(r);
            } else {
                return currentTarget.newThread(r);
            }
        }
    }
}
