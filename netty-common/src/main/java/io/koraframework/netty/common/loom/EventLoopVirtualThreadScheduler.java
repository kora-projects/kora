package io.koraframework.netty.common.loom;

import io.netty.util.*;
import io.netty.util.concurrent.*;
import org.jspecify.annotations.*;

public sealed interface EventLoopVirtualThreadScheduler
    permits LoomCarrierGroup.IoScheduler, LoomCarrierGroup.Runner, LoomCarrierGroup.StickyScheduler {

    AttributeMap attributeMap();

    EventExecutor eventLoop();

    @Nullable
    static EventLoopVirtualThreadScheduler current() {
        Thread thread = Thread.currentThread();
        if (!thread.isVirtual()) {
            return null;
        }
        if (LoomBranchSupport.isSupported()) {
            if (LoomBranchSupport.currentScheduler() instanceof EventLoopVirtualThreadScheduler elvts) {
                return elvts;
            }
            return null;
        }
        if (PrivateLoomSupport.isSupported()) {
            if (PrivateLoomSupport.getScheduler(thread) instanceof EventLoopVirtualThreadScheduler elvts) {
                return elvts;
            }
            return null;
        }
        return null;
    }
}
