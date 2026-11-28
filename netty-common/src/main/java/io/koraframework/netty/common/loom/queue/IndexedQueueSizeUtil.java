package io.koraframework.netty.common.loom.queue;

public final class IndexedQueueSizeUtil {

    public static final int IGNORE_PARITY_DIVISOR = 2;

    private IndexedQueueSizeUtil() {}

    public static int size(IndexedQueue iq, int divisor) {
        long after = iq.lvConsumerIndex();
        long size;
        while (true) {
            final long before = after;
            final long currentProducerIndex = iq.lvProducerIndex();
            after = iq.lvConsumerIndex();
            if (before == after) {
                size = (currentProducerIndex - after) / divisor;
                break;
            }
        }
        return sanitizedSize(iq.capacity(), size);
    }

    public static int sanitizedSize(int capacity, long size) {
        if (size < 0) {
            return 0;
        }
        if (capacity != MessagePassingQueue.UNBOUNDED_CAPACITY && size > capacity) {
            return capacity;
        }
        if (size > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) size;
    }

    public static boolean isEmpty(IndexedQueue iq) {
        return iq.lvConsumerIndex() >= iq.lvProducerIndex();
    }

    public interface IndexedQueue {
        long lvConsumerIndex();

        long lvProducerIndex();

        int capacity();
    }
}
