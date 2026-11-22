package io.koraframework.netty.common.loom.queue;

import static io.koraframework.netty.common.loom.queue.UnsafeRefArrayAccess.*;

public final class LinkedArrayQueueUtil {
    public static int length(Object[] buf) {
        return buf.length;
    }

    public static long modifiedCalcCircularRefElementOffset(long index, long mask) {
        return REF_ARRAY_BASE + ((index & mask) << (REF_ELEMENT_SHIFT - 1));
    }

    public static long nextArrayOffset(Object[] curr) {
        return REF_ARRAY_BASE + ((long) (length(curr) - 1) << REF_ELEMENT_SHIFT);
    }
}
