package ru.tinkoff.kora.resilient.circuitbreaker;


import javax.annotation.Nonnull;
import java.util.function.Supplier;

/**
 * A {@link CircuitBreaker} manages the state of a backend system. The CircuitBreaker is implemented
 * via a finite state machine with five states: CLOSED, OPEN, HALF_OPEN.
 * The CircuitBreaker does not know anything about the backend's state by itself, but uses the
 * information provided by the decorators via {@link CircuitBreaker#releaseOnSuccess()} and {@link
 * CircuitBreaker#releaseOnError(Throwable)} events. Before communicating with the backend, the permission to do so
 * must be obtained via the method {@link CircuitBreaker#acquire()}}.
 * <p>
 * The state of the CircuitBreaker changes from CLOSED to OPEN when the failure rate is greater than or
 * equal to a (configurable) threshold. Then, all access to the backend is rejected for a (configurable) time
 * duration. No further calls are permitted.
 * <p>
 * After the time duration has elapsed, the CircuitBreaker state changes from OPEN to HALF_OPEN and
 * allows a number of calls to see if the backend is still unavailable or has become available
 * again. If the failure rate is greater than or equal to the configured threshold, the state changes back to OPEN.
 * If the failure rate is below or equal to the threshold, the state changes back to CLOSED.
 */
public interface CircuitBreaker {

    enum State {
        CLOSED, OPEN, HALF_OPEN
    }

    /**
     * Try to acquire {@link CircuitBreaker} and return result from {@link Supplier}
     * or throws {@link CallNotPermittedException} if not acquired
     * or fails with exception from {@link Supplier} if it occurred there
     *
     * @param callable to accept to execute for result
     * @param <T>      type of result
     * @return result after {@link #tryAcquire()} was successful or throws {@link CallNotPermittedException}
     * @throws CallNotPermittedException when can't acquire
     */
    <T> T accept(@Nonnull Supplier<T> callable) throws CallNotPermittedException;

    /**
     * Try to acquire {@link CircuitBreaker} and return result from {@link Supplier} or result from {@link Supplier} fallback
     * or fails with exception from {@link Supplier} if it occurred there
     *
     * @param callable to accept to execute for result
     * @param fallback to execute if {@link #tryAcquire()} failed
     * @param <T>      type of result
     * @return result after {@link #tryAcquire()} was successful or return fallback result
     * @throws CallNotPermittedException when can't acquire
     */
    <T> T accept(@Nonnull Supplier<T> callable, @Nonnull Supplier<T> fallback) throws CallNotPermittedException;

    /**
     * Try to obtain a permission to execute a call. If a call is not permitted, the number of not
     * permitted calls is increased.
     * <p>
     * Throws a CallNotPermittedException when the state is OPEN or FORCED_OPEN. Returns when the
     * state is CLOSED or DISABLED. Returns when the state is HALF_OPEN and further test calls are
     * allowed. Throws a CallNotPermittedException when the state is HALF_OPEN and the number of
     * test calls has been reached. If the state is HALF_OPEN, the number of allowed test calls is
     * decreased. Important: Make sure to call onSuccess or onError after the call is finished. If
     * the call is cancelled before it is invoked, you have to release the permission again.
     *
     * @return false when CircuitBreaker is OPEN or HALF_OPEN and no further test calls are permitted.
     */
    boolean tryAcquire();

    /**
     * Try to obtain a permission to execute a call. If a call is not permitted, the number of not
     * permitted calls is increased.
     * <p>
     * Throws a CallNotPermittedException when the state is OPEN or FORCED_OPEN. Returns when the
     * state is CLOSED or DISABLED. Returns when the state is HALF_OPEN and further test calls are
     * allowed. Throws a CallNotPermittedException when the state is HALF_OPEN and the number of
     * test calls has been reached. If the state is HALF_OPEN, the number of allowed test calls is
     * decreased. Important: Make sure to call onSuccess or onError after the call is finished. If
     * the call is cancelled before it is invoked, you have to release the permission again.
     *
     * @throws CallNotPermittedException when CircuitBreaker is OPEN or HALF_OPEN and no further test calls are permitted.
     */
    void acquire() throws CallNotPermittedException;

    /**
     * Records a successful call. This method must be invoked when a call was
     * successful.
     */
    void releaseOnSuccess();

    /**
     * Records a failed call. This method must be invoked when a call failed.
     *
     * @param throwable The throwable which must be recorded
     */
    void releaseOnError(@Nonnull Throwable throwable);
}
