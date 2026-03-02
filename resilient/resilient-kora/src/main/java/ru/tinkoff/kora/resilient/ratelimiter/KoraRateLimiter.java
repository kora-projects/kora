package ru.tinkoff.kora.resilient.ratelimiter;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Lock-free fixed-window rate limiter implementation.
 * <p>
 * State is packed into a single {@link AtomicLong}:
 * <ul>
 *   <li>Upper 32 bits: lower 32 bits of the current period index
 *       (computed as {@code System.nanoTime() / periodNanos}).
 *       Used to detect when a new period begins.</li>
 *   <li>Lower 32 bits: number of permits used in the current period.</li>
 * </ul>
 * <p>
 * On each {@link #tryAcquire()} call:
 * <ol>
 *   <li>Compute the current period index.</li>
 *   <li>If the stored period differs from the current one, reset the counter to 1 (new period).</li>
 *   <li>If the stored count is below {@code limitForPeriod}, increment and allow.</li>
 *   <li>Otherwise, reject — rate limit exceeded.</li>
 * </ol>
 */
final class KoraRateLimiter implements RateLimiter {

    private final String name;
    private final RateLimiterConfig.NamedConfig config;
    private final RateLimiterMetrics metrics;
    private final long periodNanos;

    /**
     * State encoding:
     * - bits 63..32: lower 32 bits of the current period index
     * - bits 31..0:  permits used in the current period
     */
    private final AtomicLong state = new AtomicLong(0L);

    KoraRateLimiter(String name, RateLimiterConfig.NamedConfig config, RateLimiterMetrics metrics) {
        this.name = name;
        this.config = config;
        this.metrics = metrics;
        this.periodNanos = config.limitRefreshPeriod().toNanos();
    }

    @Override
    public boolean tryAcquire() {
        if (Boolean.FALSE.equals(config.enabled())) {
            metrics.recordAcquire(name, true);
            return true;
        }

        final long nowNanos = System.nanoTime();
        // Lower 32 bits of the period index — sufficient for period change detection
        final long currentPeriodMark = (nowNanos / periodNanos) & 0xFFFFFFFFL;
        final int limitForPeriod = config.limitForPeriod();

        while (true) {
            final long current = state.get();
            final long storedPeriodMark = current >>> 32;
            final long storedCount = current & 0xFFFFFFFFL;

            final long newPeriodMark;
            final long newCount;

            if (storedPeriodMark != currentPeriodMark) {
                // New period started — reset the counter
                newPeriodMark = currentPeriodMark;
                newCount = 1L;
            } else if (storedCount < limitForPeriod) {
                // Within the same period and still have capacity
                newPeriodMark = storedPeriodMark;
                newCount = storedCount + 1L;
            } else {
                // Rate limit exceeded for this period
                metrics.recordAcquire(name, false);
                return false;
            }

            final long newState = (newPeriodMark << 32) | newCount;
            if (state.compareAndSet(current, newState)) {
                metrics.recordAcquire(name, true);
                return true;
            }
            // CAS failed due to concurrent update — retry
        }
    }

    @Override
    public void acquire() throws RateLimitExceededException {
        if (!tryAcquire()) {
            throw new RateLimitExceededException(name);
        }
    }
}
