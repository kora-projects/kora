package io.koraframework.resilient.distributed.ratelimiter;

/**
 * Backend abstraction over the atomic counter primitives a distributed {@link io.koraframework.resilient.ratelimiter.RateLimiter}
 * needs. It hides the storage engine (Redis, etc.) from the rate limiting algorithms.
 *
 * <p>The provided algorithms — {@link FixedWindowRateLimiter} and {@link TokenBucketRateLimiter} (GCRA) — rely only on
 * atomic increments and a plain set, so no server-side scripting is required.
 */
public interface DistributedRateLimiterClient {

    /**
     * Atomically increments the counter stored at {@code key} by one and returns the new value, making sure the key
     * expires no later than {@code ttlMillis} from creation (so idle windows are reclaimed automatically).
     *
     * @param key       the counter key
     * @param ttlMillis time-to-live to apply to the key on creation, in milliseconds
     * @return the counter value after the increment
     */
    long incrementAndExpire(String key, long ttlMillis);

    /**
     * Atomically adds {@code delta} (which may be negative) to the value stored at {@code key}, (re)sets its expiry to
     * {@code ttlMillis} from now, and returns the new value. A missing key is treated as {@code 0} before the add.
     *
     * @param key       the value key
     * @param delta     amount to add; negative to subtract
     * @param ttlMillis time-to-live to apply to the key, in milliseconds
     * @return the value after adding {@code delta}
     */
    long addAndExpire(String key, long delta, long ttlMillis);

    /**
     * Unconditionally stores {@code value} at {@code key} with an expiry of {@code ttlMillis} from now.
     *
     * @param key       the value key
     * @param value     the value to store
     * @param ttlMillis time-to-live to apply to the key, in milliseconds
     */
    void set(String key, long value, long ttlMillis);
}
