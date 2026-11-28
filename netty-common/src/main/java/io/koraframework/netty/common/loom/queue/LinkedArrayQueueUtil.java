package io.koraframework.netty.common.loom.queue;

public final class LinkedArrayQueueUtil {

    private LinkedArrayQueueUtil() {}

    public static int modifiedCalcCircularRefElementIndex(long index, long mask) {
        return (int) ((index & mask) >> 1);
    }

    public static int nextArrayIndex(Object[] curr) {
        return curr.length - 1;
    }
}
