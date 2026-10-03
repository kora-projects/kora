package io.koraframework.resilient.bulkhead;

import io.koraframework.resilient.bulkhead.exception.BulkheadFullException;
import io.koraframework.resilient.common.ThrowableCallable;
import io.koraframework.resilient.common.ThrowableRunnable;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.jspecify.annotations.Nullable;

/**
 * Local concurrency isolation for a group of operations sharing one typed spec instance. Capacity
 * becomes available when an operation releases its permit. The budget counts admitted operations,
 * independently of thread count, connection pools and arrival rate.
 * <p>
 * Use a bulkhead to keep calls to one resource from consuming an unbounded share of application
 * capacity. {@link #execute(ThrowableCallable)} covers a synchronous call,
 * {@link #executeAsync(ThrowableCallable)} covers a completion stage, and an explicit
 * {@link Permit} covers a resource whose lifetime extends beyond the method return.
 * <p>
 * {@link KoraBulkhead} selects a fixed, AIMD or throughput-oriented limit through
 * {@link BulkheadConfig#type()}. Queueing is independent of that choice: every implementation can
 * reject immediately or use a bounded FIFO queue with a timeout. No execution pool is introduced by
 * a bulkhead.
 */
public interface Bulkhead {

    String name();

    int maxConcurrentCalls();

    /**
     * Current admission limit (bounded by maxConcurrentCalls).
     */
    default int currentLimit() {
        return maxConcurrentCalls();
    }

    default int queueLength() {
        return 0;
    }

    int inFlight();

    long rejectedCalls();

    /**
     * Never waits or queues. Returns an owned permit, or {@code null} when full.
     */
    @Nullable Permit tryAcquire();

    default Permit acquire() {
        var permit = tryAcquire();
        if (permit == null) {
            throw new BulkheadFullException(name());
        }
        return permit;
    }

    /**
     * Cancellable admission; cancelling a queued request removes it from the queue.
     */
    default CompletionStage<Permit> acquireAsync() {
        return CompletableFuture.completedFuture(acquire());
    }

    /**
     * Runs on the caller's thread and releases capacity when the call returns or throws. A returned
     * lazy body, stream or future is not covered after this method returns; use an explicit permit for
     * such resource lifetimes.
     */
    default <T, E extends Throwable> T execute(ThrowableCallable<T, E> action) throws E {
        Objects.requireNonNull(action, "action");
        var permit = acquire();
        try {
            return action.call();
        } catch (Throwable error) {
            permit.observeError(error);
            throw error;
        } finally {
            permit.close();
        }
    }

    default <E extends Throwable> void execute(ThrowableRunnable<E> action) throws E {
        Objects.requireNonNull(action, "action");
        var permit = acquire();
        try {
            action.run();
        } catch (Throwable error) {
            permit.observeError(error);
            throw error;
        } finally {
            permit.close();
        }
    }

    /**
     * Releases capacity when the supplied stage terminates, before publishing its result. The stage
     * must represent the entire protected operation. Cancelling the returned future neither cancels the
     * source nor releases its permit early. A source stage cancelled while its underlying work
     * continues cannot represent that work's lifetime; use an explicit permit released by the actual
     * termination callback instead. Immediate rejection is thrown synchronously. Queued execution
     * failures are delivered through the returned stage.
     */
    default <T, E extends Throwable> CompletionStage<T> executeAsync(ThrowableCallable<? extends CompletionStage<T>, E> action) throws E {
        Objects.requireNonNull(action, "action");
        var permit = acquire();
        try {
            var source = Objects.requireNonNull(action.call(), "action returned null stage");
            var result = new CompletableFuture<T>();
            source.whenComplete((value, error) -> {
                try {
                    try {
                        if (error != null) {
                            permit.observeError(error);
                        }
                    } finally {
                        permit.close();
                    }
                    if (error == null) {
                        result.complete(value);
                    } else {
                        result.completeExceptionally(error);
                    }
                } catch (Throwable observationFailure) {
                    result.completeExceptionally(observationFailure);
                }
            });
            return result;
        } catch (Throwable error) {
            try {
                permit.observeError(error);
            } finally {
                permit.close();
            }
            throw error;
        }
    }

    /**
     * Owned capacity; may be closed from another thread. Closing is idempotent.
     */
    interface Permit extends AutoCloseable {

        /**
         * Records the operation failure before closing its observation.
         */
        void observeError(Throwable error);

        @Override
        void close();
    }
}
