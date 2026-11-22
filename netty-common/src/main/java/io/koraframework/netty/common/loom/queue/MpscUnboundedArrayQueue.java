package io.koraframework.netty.common.loom.queue;

import java.util.function.*;

public class MpscUnboundedArrayQueue<E> extends BaseMpscLinkedArrayQueue<E> {
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;

    public MpscUnboundedArrayQueue(int chunkSize) {
        super(chunkSize);
    }

    @Override
    protected long availableInQueue(long pIndex, long cIndex) {
        return Integer.MAX_VALUE;
    }

    @Override
    public int capacity() {
        return UNBOUNDED_CAPACITY;
    }

    @Override
    public int drain(Consumer<E> c) {
        return drain(c, 4096);
    }

    @Override
    protected int getNextBufferSize(E[] buffer) {
        return buffer.length;
    }

    @Override
    protected long getCurrentBufferCapacity(long mask) {
        return mask;
    }
}
