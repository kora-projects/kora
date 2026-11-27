package io.koraframework.common.context;

import org.jspecify.annotations.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

public final class KoraContext {

    private static final ScopedValue<KoraContext> SCOPED_VALUE = ScopedValue.newInstance();
    private static final int DEFAULT_INITIAL_CAPACITY = 4;

    private static int maxUsedCapacity = DEFAULT_INITIAL_CAPACITY;

    private Object[] values;

    private KoraContext(Object[] values) {
        this.values = values;
    }

    public static <A, B, C, D> KoraContext of(
        Key<A> k1, A v1,
        Key<B> k2, B v2,
        Key<C> k3, C v3,
        Key<D> k4, D v4
    ) {
        int maxIdx = Math.max(Math.max(k1.index, k2.index), Math.max(k3.index, k4.index));
        int cap = Math.max(maxUsedCapacity, maxIdx + 1);
        if (cap > maxUsedCapacity) {
            maxUsedCapacity = cap;
        }
        var arr = new Object[cap];
        arr[k1.index] = v1;
        arr[k2.index] = v2;
        arr[k3.index] = v3;
        arr[k4.index] = v4;
        return new KoraContext(arr);
    }

    public static KoraContext empty() {
        return new KoraContext(new Object[maxUsedCapacity]);
    }

    public static @Nullable KoraContext current() {
        return SCOPED_VALUE.orElse(null);
    }

    public static <R> R runWith(KoraContext context, Supplier<R> action) {
        return ScopedValue.where(SCOPED_VALUE, context).call(action::get);
    }

    public static void runWith(KoraContext context, Runnable action) {
        ScopedValue.where(SCOPED_VALUE, context).run(action);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
        throw (E) e;
    }

    public <R> R call(Callable<R> action) {
        return ScopedValue.where(SCOPED_VALUE, this).call(() -> {
            try {
                return action.call();
            } catch (Throwable e) {
                sneakyThrow(e);
                return null;
            }
        });
    }

    public void run(Runnable action) {
        ScopedValue.where(SCOPED_VALUE, this).run(action);
    }

    public <T> @Nullable T get(Key<T> key) {
        int index = key.index;
        Object[] arr = values;
        if (index < arr.length) {
            @SuppressWarnings("unchecked")
            var value = (T) arr[index];
            return value;
        }
        return null;
    }

    public <T> KoraContext with(Key<T> key, @Nullable T value) {
        int index = key.index;
        Object[] arr = values;
        if (index >= arr.length) {
            int newCap = index + 1;
            arr = Arrays.copyOf(arr, newCap);
            values = arr;
            if (newCap > maxUsedCapacity) {
                maxUsedCapacity = newCap;
            }
        }
        arr[index] = value;
        return this;
    }

    public static final class Key<T> {

        private static final AtomicInteger INDEX_COUNTER = new AtomicInteger(0);

        final int index;

        private Key(int index) {
            this.index = index;
        }

        public static <T> Key<T> create() {
            return new Key<>(INDEX_COUNTER.getAndIncrement());
        }
    }
}
