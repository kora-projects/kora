package io.koraframework.resilient.ratelimiter;

import io.koraframework.resilient.ratelimiter.telemetry.RateLimiterTelemetry;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Count-based fixed window rate limiter.
 *
 * <p>Lock-free: a single {@link AtomicLong} packs the current window id and its counter, updated with one CAS per call.
 * Cheapest option, but allows up to twice the limit across the boundary of two adjacent windows.
 *
 * <p>Packing: window id in the high 40 bits, counter in the low {@value #COUNT_BITS} bits — so {@code limitForPeriod}
 * must be below 2^{@value #COUNT_BITS}.
 */
final class FixedWindowKoraRateLimiter extends AbstractKoraRateLimiter {

    private static final int COUNT_BITS = 24;
    private static final long COUNT_MASK = (1L << COUNT_BITS) - 1;
    private static final long WINDOW_MASK = (1L << (Long.SIZE - COUNT_BITS)) - 1;

    private final long startedNanos = System.nanoTime();
    // packed state: (windowId << COUNT_BITS) | count
    private final AtomicLong state = new AtomicLong();

    FixedWindowKoraRateLimiter(String name, RateLimiterConfig config, RateLimiterTelemetry telemetry) {
        super(name, config, telemetry);
    }

    @Override
    protected boolean doTryAcquire() {
        final long windowId = (elapsedNanos() / windowNanos) & WINDOW_MASK;
        while (true) {
            final long s = state.get();
            final long currentWindow = s >>> COUNT_BITS;
            final long count = s & COUNT_MASK;
            // sign-extended difference within the window-bit width, so wrap-around still compares correctly
            final long diff = ((windowId - currentWindow) << COUNT_BITS) >> COUNT_BITS;
            final long next;
            if (diff > 0) {
                next = (windowId << COUNT_BITS) | 1L;
            } else if (count < limitForPeriod) {
                // diff < 0: this caller read the clock before another caller rolled the window forward;
                // count it against the newer window instead of moving the window back
                next = s + 1L;
            } else {
                return false;
            }
            if (state.compareAndSet(s, next)) {
                return true;
            }
        }
    }

    private long elapsedNanos() {
        return Math.max(0, System.nanoTime() - startedNanos);
    }

    @Override
    public String toString() {
        return "FixedWindowKoraRateLimiter{name='" + name + '\''
            + ", enabled=" + enabled
            + ", limitForPeriod=" + limitForPeriod
            + ", availablePermissions=" + Math.max(0, limitForPeriod - (state.get() & COUNT_MASK))
            + '}';
    }
}
