package io.koraframework.netty.common.loom.queue;

import static io.koraframework.netty.common.loom.queue.UnsafeAccess.*;

public final class UnsafeRefArrayAccess {
    public static final long REF_ARRAY_BASE;
    public static final int REF_ELEMENT_SHIFT;

    static {
        final int scale = UNSAFE.arrayIndexScale(Object[].class);
        if (4 == scale) {
            REF_ELEMENT_SHIFT = 2;
        } else if (8 == scale) {
            REF_ELEMENT_SHIFT = 3;
        } else {
            throw new IllegalStateException("Unknown pointer size: " + scale);
        }
        REF_ARRAY_BASE = UNSAFE.arrayBaseOffset(Object[].class);
    }

    public static <E> void spRefElement(E[] buffer, long offset, E e) {
        UNSAFE.putObject(buffer, offset, e);
    }

    public static <E> void soRefElement(E[] buffer, long offset, E e) {
        UNSAFE.putOrderedObject(buffer, offset, e);
    }

    @SuppressWarnings("unchecked")
    public static <E> E lpRefElement(E[] buffer, long offset) {
        return (E) UNSAFE.getObject(buffer, offset);
    }

    @SuppressWarnings("unchecked")
    public static <E> E lvRefElement(E[] buffer, long offset) {
        return (E) UNSAFE.getObjectVolatile(buffer, offset);
    }

    public static long calcRefElementOffset(long index) {
        return REF_ARRAY_BASE + (index << REF_ELEMENT_SHIFT);
    }

    public static long calcCircularRefElementOffset(long index, long mask) {
        return REF_ARRAY_BASE + ((index & mask) << REF_ELEMENT_SHIFT);
    }

    @SuppressWarnings("unchecked")
    public static <E> E[] allocateRefArray(int capacity) {
        return (E[]) new Object[capacity];
    }
}
