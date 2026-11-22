package io.koraframework.netty.common.loom.queue;

import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public final class RefArrayAccess {
    private static final VarHandle ARRAY_HANDLE = MethodHandles.arrayElementVarHandle(Object[].class);

    private RefArrayAccess() {}

    public static <E> void spRefElement(E[] buffer, int index, @Nullable E e) {
        buffer[index] = e;
    }

    public static <E> void soRefElement(E[] buffer, int index, @Nullable E e) {
        ARRAY_HANDLE.setRelease(buffer, index, e);
    }

    @Nullable
    public static <E> E lpRefElement(E[] buffer, int index) {
        return buffer[index];
    }

    @SuppressWarnings("unchecked")
    @Nullable
    public static <E> E lvRefElement(E[] buffer, int index) {
        return (E) ARRAY_HANDLE.getVolatile(buffer, index);
    }

    public static int calcCircularRefElementIndex(long index, long mask) {
        return (int) (index & mask);
    }

    @SuppressWarnings("unchecked")
    public static <E> E[] allocateRefArray(int capacity) {
        return (E[]) new Object[capacity];
    }
}
