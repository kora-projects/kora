package io.koraframework.resilient.distributed.retry;

/**
 * Backend abstraction over the atomic floating-point counter a distributed {@link io.koraframework.resilient.retry.RetryBudget}
 * needs. It hides the storage engine (Redis, etc.) from the budget logic.
 *
 * <p>The budget is a shared token balance: successes deposit a fractional amount and each retry withdraws one token. Both
 * operations map onto a single atomic "add delta and return the new value" primitive, so no server-side scripting is
 * required — the {@link DistributedRetryBudget} decides on the returned value and compensates when a withdrawal would go
 * negative.
 */
public interface DistributedRetryBudgetClient {

    /**
     * Initializes the balance at {@code key} to {@code initial} if it does not yet exist, then atomically adds
     * {@code delta} (which may be negative) and returns the resulting balance. The key is (re)set to expire after
     * {@code ttlMillis} so idle budgets are reclaimed.
     *
     * @param key       the balance key
     * @param delta     amount to add; negative to withdraw
     * @param initial   value to seed a missing key with before applying {@code delta}
     * @param ttlMillis time-to-live to apply to the key, in milliseconds
     * @return the balance after adding {@code delta}
     */
    double addAndGet(String key, double delta, double initial, long ttlMillis);

    /**
     * Returns the current balance at {@code key}, or {@code initial} if the key is absent.
     *
     * @param key     the balance key
     * @param initial value returned when the key is missing
     * @return the current balance
     */
    double get(String key, double initial);
}
