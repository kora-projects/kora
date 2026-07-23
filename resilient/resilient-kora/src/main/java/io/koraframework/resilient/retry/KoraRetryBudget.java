package io.koraframework.resilient.retry;

import java.util.concurrent.atomic.AtomicLong;

public final class KoraRetryBudget {

    static final long SCALE = 1_000_000L;

    private final AtomicLong tokens;
    private final long tokensMax;
    private final long successIncrement;
    private final long minTokenIncrementPerSecond;
    private final AtomicLong lastMinTokenRefillNanos = new AtomicLong(System.nanoTime());

    public KoraRetryBudget(RetryConfig.RetryBudgetConfig config) {
        this(config.ratio(), config.tokensMax(), config.tokensInitial(), config.minTokensPerSecond());
    }

    public KoraRetryBudget(double ratio, int tokensMax, int tokensInitial, double minTokensPerSecond) {
        if (ratio < 0) {
            throw new IllegalArgumentException("RetryBudget ratio must be >= 0");
        }
        if (tokensMax < 0) {
            throw new IllegalArgumentException("RetryBudget tokensMax must be >= 0");
        }
        if (tokensInitial < 0) {
            throw new IllegalArgumentException("RetryBudget tokensInitial must be >= 0");
        }
        if (tokensInitial > tokensMax) {
            throw new IllegalArgumentException("RetryBudget tokensInitial must be <= tokensMax");
        }
        if (minTokensPerSecond < 0) {
            throw new IllegalArgumentException("RetryBudget minTokensPerSecond must be >= 0");
        }
        this.tokens = new AtomicLong(scale(tokensInitial));
        this.tokensMax = scale(tokensMax);
        this.successIncrement = Math.round(ratio * SCALE);
        this.minTokenIncrementPerSecond = Math.round(minTokensPerSecond * SCALE);
    }

    public boolean tryAcquireRetryToken() {
        refillMinTokens();
        while (true) {
            long current = tokens.get();
            if (current < SCALE) {
                return false;
            }
            if (tokens.compareAndSet(current, current - SCALE)) {
                return true;
            }
        }
    }

    public void onSuccess() {
        addTokens(successIncrement);
    }

    public double availableTokens() {
        return tokens.get() / (double) SCALE;
    }

    private void refillMinTokens() {
        if (minTokenIncrementPerSecond <= 0) {
            return;
        }

        var now = System.nanoTime();
        var previous = lastMinTokenRefillNanos.get();
        var elapsed = now - previous;
        if (elapsed <= 0) {
            return;
        }

        var refill = minTokenIncrementPerSecond * elapsed / 1_000_000_000L;
        if (refill <= 0) {
            return;
        }

        if (lastMinTokenRefillNanos.compareAndSet(previous, now)) {
            addTokens(refill);
        }
    }

    private void addTokens(long amount) {
        while (true) {
            long current = tokens.get();
            long next = Math.min(tokensMax, current + amount);
            if (tokens.compareAndSet(current, next)) {
                return;
            }
        }
    }

    private static long scale(int tokens) {
        return tokens * SCALE;
    }
}
