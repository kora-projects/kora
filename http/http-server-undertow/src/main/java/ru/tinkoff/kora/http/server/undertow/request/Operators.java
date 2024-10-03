package ru.tinkoff.kora.http.server.undertow.request;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

public final class Operators {

    private Operators() { }

    public static <T> long addCap(AtomicLongFieldUpdater<T> updater, T instance, long toAdd) {
        long r, u;
        for (;;) {
            r = updater.get(instance);
            if (r == Long.MAX_VALUE) {
                return Long.MAX_VALUE;
            }
            u = addCap(r, toAdd);
            if (updater.compareAndSet(instance, r, u)) {
                return r;
            }
        }
    }
    public static long addCap(long a, long b) {
        long res = a + b;
        if (res < 0L) {
            return Long.MAX_VALUE;
        }
        return res;
    }
}
