package io.koraframework.netty.common.loom.queue;

import org.jspecify.annotations.*;

import java.lang.invoke.*;
import java.util.*;
import java.util.function.*;

import static io.koraframework.netty.common.loom.queue.LinkedArrayQueueUtil.*;
import static io.koraframework.netty.common.loom.queue.RefArrayAccess.*;

abstract class BaseMpscLinkedArrayQueuePad1<E> extends AbstractQueue<E> implements IndexedQueueSizeUtil.IndexedQueue {
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;
}

abstract class BaseMpscLinkedArrayQueueProducerFields<E> extends BaseMpscLinkedArrayQueuePad1<E> {
    private static final VarHandle P_INDEX;

    static {
        try {
            P_INDEX = MethodHandles.lookup().findVarHandle(
                BaseMpscLinkedArrayQueueProducerFields.class, "producerIndex", long.class
            );
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private volatile long producerIndex;

    @Override
    public final long lvProducerIndex() {
        return producerIndex;
    }

    final void soProducerIndex(long newValue) {
        P_INDEX.setRelease(this, newValue);
    }

    final boolean casProducerIndex(long expect, long newValue) {
        return P_INDEX.compareAndSet(this, expect, newValue);
    }
}

abstract class BaseMpscLinkedArrayQueuePad2<E> extends BaseMpscLinkedArrayQueueProducerFields<E> {
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;
}

abstract class BaseMpscLinkedArrayQueueConsumerFields<E> extends BaseMpscLinkedArrayQueuePad2<E> {
    private static final VarHandle C_INDEX;

    static {
        try {
            C_INDEX = MethodHandles.lookup().findVarHandle(
                BaseMpscLinkedArrayQueueConsumerFields.class, "consumerIndex", long.class
            );
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private long consumerIndex;
    protected long consumerMask;
    protected E[] consumerBuffer;

    @Override
    public final long lvConsumerIndex() {
        return (long) C_INDEX.getVolatile(this);
    }

    final long lpConsumerIndex() {
        return consumerIndex;
    }

    final void soConsumerIndex(long newValue) {
        C_INDEX.setRelease(this, newValue);
    }
}

abstract class BaseMpscLinkedArrayQueuePad3<E> extends BaseMpscLinkedArrayQueueConsumerFields<E> {
    long p00, p01, p02, p03, p04, p05, p06, p07;
    long p10, p11, p12, p13, p14, p15, p16, p17;
}

abstract class BaseMpscLinkedArrayQueueColdProducerFields<E> extends BaseMpscLinkedArrayQueuePad3<E> {
    private static final VarHandle P_LIMIT;

    static {
        try {
            P_LIMIT = MethodHandles.lookup().findVarHandle(
                BaseMpscLinkedArrayQueueColdProducerFields.class, "producerLimit", long.class
            );
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private volatile long producerLimit;
    protected long producerMask;
    protected E[] producerBuffer;

    final long lvProducerLimit() {
        return producerLimit;
    }

    final boolean casProducerLimit(long expect, long newValue) {
        return P_LIMIT.compareAndSet(this, expect, newValue);
    }

    final void soProducerLimit(long newValue) {
        P_LIMIT.setRelease(this, newValue);
    }
}

abstract class BaseMpscLinkedArrayQueue<E> extends BaseMpscLinkedArrayQueueColdProducerFields<E>
    implements MessagePassingQueue<E> {
    private static final Object JUMP = new Object();
    private static final Object BUFFER_CONSUMED = new Object();
    private static final int CONTINUE_TO_P_INDEX_CAS = 0;
    private static final int RETRY = 1;
    private static final int QUEUE_FULL = 2;
    private static final int QUEUE_RESIZE = 3;

    public BaseMpscLinkedArrayQueue(final int initialCapacity) {
        if (initialCapacity < 2) {
            throw new IllegalArgumentException("initialCapacity: " + initialCapacity + " (expected: >= 2)");
        }

        int p2capacity = Pow2.roundToPowerOfTwo(initialCapacity);
        long mask = (long) (p2capacity - 1) << 1;
        E[] buffer = allocateRefArray(p2capacity + 1);
        producerBuffer = buffer;
        producerMask = mask;
        consumerBuffer = buffer;
        consumerMask = mask;
        soProducerLimit(mask);
    }

    @Override
    public int size() {
        return IndexedQueueSizeUtil.size(this, IndexedQueueSizeUtil.IGNORE_PARITY_DIVISOR);
    }

    @Override
    public boolean isEmpty() {
        return ((lvConsumerIndex() - lvProducerIndex()) / 2 == 0);
    }

    @Override
    public String toString() {
        return this.getClass().getName();
    }

    @Override
    public boolean offer(final E e) {
        if (null == e) {
            throw new NullPointerException();
        }

        long mask;
        E[] buffer;
        long pIndex;

        while (true) {
            long producerLimit = lvProducerLimit();
            pIndex = lvProducerIndex();
            if ((pIndex & 1) == 1) {
                continue;
            }

            mask = this.producerMask;
            buffer = this.producerBuffer;

            if (producerLimit <= pIndex) {
                int result = offerSlowPath(mask, pIndex, producerLimit);
                switch (result) {
                    case CONTINUE_TO_P_INDEX_CAS:
                        break;
                    case RETRY:
                        continue;
                    case QUEUE_FULL:
                        return false;
                    case QUEUE_RESIZE:
                        resize(mask, buffer, pIndex, e);
                        return true;
                }
            }

            if (casProducerIndex(pIndex, pIndex + 2)) {
                break;
            }
        }
        final int idx = modifiedCalcCircularRefElementIndex(pIndex, mask);
        soRefElement(buffer, idx, e);
        return true;
    }

    @SuppressWarnings("unchecked")
    @Override
    @Nullable
    public E poll() {
        final E[] buffer = consumerBuffer;
        final long cIndex = lpConsumerIndex();
        final long mask = consumerMask;

        final int idx = modifiedCalcCircularRefElementIndex(cIndex, mask);
        Object e = lvRefElement(buffer, idx);
        if (e == null) {
            long pIndex = lvProducerIndex();
            if ((cIndex - pIndex) / 2 == 0) {
                return null;
            }
            do {
                e = lvRefElement(buffer, idx);
            } while (e == null);
        }

        if (e == JUMP) {
            final E[] nextBuffer = nextBuffer(buffer, mask);
            return newBufferPoll(nextBuffer, cIndex);
        }

        soRefElement(buffer, idx, null);
        soConsumerIndex(cIndex + 2);
        return (E) e;
    }

    @SuppressWarnings("unchecked")
    @Override
    @Nullable
    public E peek() {
        final E[] buffer = consumerBuffer;
        final long cIndex = lpConsumerIndex();
        final long mask = consumerMask;

        final int idx = modifiedCalcCircularRefElementIndex(cIndex, mask);
        Object e = lvRefElement(buffer, idx);
        if (e == null) {
            long pIndex = lvProducerIndex();
            if ((cIndex - pIndex) / 2 == 0) {
                return null;
            }
            do {
                e = lvRefElement(buffer, idx);
            } while (e == null);
        }
        if (e == JUMP) {
            return newBufferPeek(nextBuffer(buffer, mask), cIndex);
        }
        return (E) e;
    }

    private int offerSlowPath(long mask, long pIndex, long producerLimit) {
        final long cIndex = lvConsumerIndex();
        long bufferCapacity = getCurrentBufferCapacity(mask);

        if (cIndex + bufferCapacity > pIndex) {
            if (!casProducerLimit(producerLimit, cIndex + bufferCapacity)) {
                return RETRY;
            } else {
                return CONTINUE_TO_P_INDEX_CAS;
            }
        } else if (availableInQueue(pIndex, cIndex) <= 0) {
            return QUEUE_FULL;
        } else if (casProducerIndex(pIndex, pIndex + 1)) {
            return QUEUE_RESIZE;
        } else {
            return RETRY;
        }
    }

    protected abstract long availableInQueue(long pIndex, long cIndex);

    @SuppressWarnings("unchecked")
    private E[] nextBuffer(final E[] buffer, final long mask) {
        final int nextIdx = nextArrayIndex(mask);
        final E[] nextBuffer = (E[]) lvRefElement(buffer, nextIdx);
        consumerBuffer = nextBuffer;
        consumerMask = (long) (nextBuffer.length - 2) << 1;
        soRefElement(buffer, nextIdx, BUFFER_CONSUMED);
        return nextBuffer;
    }

    private static int nextArrayIndex(long mask) {
        return modifiedCalcCircularRefElementIndex(mask + 2, Long.MAX_VALUE);
    }

    @Nullable
    private E newBufferPoll(E[] nextBuffer, long cIndex) {
        final int idx = modifiedCalcCircularRefElementIndex(cIndex, consumerMask);
        final E n = lvRefElement(nextBuffer, idx);
        if (n == null) {
            throw new IllegalStateException("new buffer must have at least one element");
        }
        soRefElement(nextBuffer, idx, null);
        soConsumerIndex(cIndex + 2);
        return n;
    }

    @Nullable
    private E newBufferPeek(E[] nextBuffer, long cIndex) {
        final int idx = modifiedCalcCircularRefElementIndex(cIndex, consumerMask);
        final E n = lvRefElement(nextBuffer, idx);
        if (null == n) {
            throw new IllegalStateException("new buffer must have at least one element");
        }
        return n;
    }

    @Override
    public abstract int capacity();

    @Override
    public boolean relaxedOffer(E e) {
        return offer(e);
    }

    @SuppressWarnings("unchecked")
    @Override
    @Nullable
    public E relaxedPoll() {
        final E[] buffer = consumerBuffer;
        final long cIndex = lpConsumerIndex();
        final long mask = consumerMask;

        final int idx = modifiedCalcCircularRefElementIndex(cIndex, mask);
        Object e = lvRefElement(buffer, idx);
        if (e == null) {
            return null;
        }
        if (e == JUMP) {
            final E[] nextBuffer = nextBuffer(buffer, mask);
            return newBufferPoll(nextBuffer, cIndex);
        }
        soRefElement(buffer, idx, null);
        soConsumerIndex(cIndex + 2);
        return (E) e;
    }

    @SuppressWarnings("unchecked")
    @Override
    @Nullable
    public E relaxedPeek() {
        final E[] buffer = consumerBuffer;
        final long cIndex = lpConsumerIndex();
        final long mask = consumerMask;

        final int idx = modifiedCalcCircularRefElementIndex(cIndex, mask);
        Object e = lvRefElement(buffer, idx);
        if (e == JUMP) {
            return newBufferPeek(nextBuffer(buffer, mask), cIndex);
        }
        return (E) e;
    }

    @Override
    public int drain(Consumer<E> c) {
        return drain(c, capacity());
    }

    @Override
    public int drain(Consumer<E> c, int limit) {
        return MessagePassingQueueUtil.drain(this, c, limit);
    }

    @Override
    public Iterator<E> iterator() {
        throw new UnsupportedOperationException();
    }

    private void resize(long oldMask, E[] oldBuffer, long pIndex, E e) {
        int newBufferLength = getNextBufferSize(oldBuffer);
        final E[] newBuffer;
        try {
            newBuffer = allocateRefArray(newBufferLength);
        } catch (OutOfMemoryError oom) {
            soProducerIndex(pIndex);
            throw oom;
        }

        producerBuffer = newBuffer;
        final long newMask = (long) (newBufferLength - 2) << 1;
        producerMask = newMask;

        final int idxInOld = modifiedCalcCircularRefElementIndex(pIndex, oldMask);
        final int idxInNew = modifiedCalcCircularRefElementIndex(pIndex, newMask);

        soRefElement(newBuffer, idxInNew, e);
        soRefElement(oldBuffer, nextArrayIndex(oldMask), newBuffer);

        final long cIndex = lvConsumerIndex();
        final long availableInQueue = availableInQueue(pIndex, cIndex);
        if (availableInQueue <= 0) {
            throw new IllegalStateException("availableInQueue: " + availableInQueue + " (expected: > 0)");
        }

        soProducerLimit(pIndex + Math.min(newMask, availableInQueue));
        soProducerIndex(pIndex + 2);
        soRefElement(oldBuffer, idxInOld, JUMP);
    }

    protected abstract int getNextBufferSize(E[] buffer);

    protected abstract long getCurrentBufferCapacity(long mask);
}
