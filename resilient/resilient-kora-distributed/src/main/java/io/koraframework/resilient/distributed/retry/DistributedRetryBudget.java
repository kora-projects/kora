package io.koraframework.resilient.distributed.retry;

import io.koraframework.resilient.retry.RetryBudget;

/**
 * Distributed {@link RetryBudget} sharing one token balance across all instances through a {@link DistributedRetryBudgetClient}.
 *
 * <p>Semantics mirror the in-JVM {@link io.koraframework.resilient.retry.KoraRetryBudget}: each retry withdraws one token
 * and each success deposits {@code successIncrement} tokens, capped at {@code capacity}. Atomicity is achieved without
 * server-side scripting by optimistically withdrawing and refunding: {@link #tryAcquireRetryToken()} subtracts a token
 * and, if the balance went negative, immediately adds it back and denies the retry.
 *
 * <p>Unlike the in-JVM budget this implementation does not perform time-based minimum refill
 * ({@code minTokensPerSecond}) — that would require a read-modify-write against a stored timestamp, i.e. a Lua script.
 * The balance is instead driven purely by successes and retries.
 */
public final class DistributedRetryBudget implements RetryBudget {

    private final String key;
    private final double capacity;
    private final double initial;
    private final double successIncrement;
    private final long ttlMillis;
    private final DistributedRetryBudgetClient client;

    public DistributedRetryBudget(String key,
                                  double capacity,
                                  double initial,
                                  double successIncrement,
                                  long ttlMillis,
                                  DistributedRetryBudgetClient client) {
        this.key = key;
        this.capacity = capacity;
        this.initial = initial;
        this.successIncrement = successIncrement;
        this.ttlMillis = ttlMillis;
        this.client = client;
    }

    @Override
    public boolean tryAcquireRetryToken() {
        final double balance = client.addAndGet(key, -1.0, initial, ttlMillis);
        if (balance >= 0) {
            return true;
        }
        // balance went negative: refund the token we optimistically withdrew and deny the retry
        client.addAndGet(key, 1.0, initial, ttlMillis);
        return false;
    }

    @Override
    public void onSuccess() {
        final double balance = client.addAndGet(key, successIncrement, initial, ttlMillis);
        if (balance > capacity) {
            // best-effort clamp back down to capacity
            client.addAndGet(key, capacity - balance, initial, ttlMillis);
        }
    }

    @Override
    public double availableTokens() {
        return client.get(key, initial);
    }

    @Override
    public String toString() {
        return "DistributedRetryBudget{key='" + key + '\''
            + ", capacity=" + capacity
            + ", successIncrement=" + successIncrement
            + '}';
    }
}
