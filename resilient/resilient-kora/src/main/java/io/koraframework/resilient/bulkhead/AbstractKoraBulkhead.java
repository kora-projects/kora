package io.koraframework.resilient.bulkhead;

import io.koraframework.application.graph.Lifecycle;
import io.koraframework.common.Principal;
import io.koraframework.common.telemetry.Observation;
import io.koraframework.common.telemetry.OpentelemetryContext;
import io.koraframework.logging.common.MDC;
import io.koraframework.resilient.bulkhead.exception.BulkheadFullException;
import io.koraframework.resilient.bulkhead.exception.BulkheadFullException.Reason;
import io.koraframework.resilient.bulkhead.telemetry.BulkheadObservation;
import io.koraframework.resilient.bulkhead.telemetry.BulkheadTelemetry;
import io.koraframework.resilient.circuitbreaker.NonCircuitableException;
import io.koraframework.resilient.common.ThrowableCallable;
import io.koraframework.resilient.retry.NonRetryableException;
import io.opentelemetry.context.Context;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;
import org.jspecify.annotations.Nullable;

/**
 * Shared bounded admission, telemetry and permit ownership for every concurrency strategy. A lock
 * serializes reservations, FIFO queue updates and completion feedback; callbacks and queued
 * handoffs run outside that lock.
 * <p>
 * {@link #tryAcquire()} never queues or bypasses waiting callers. Blocking and asynchronous
 * acquisition can use the configured bounded queue, with timeout and queued cancellation. Queue
 * entries do not count as in-flight operations. A permit releases its reserved slot once,
 * independently of the thread that closes it.
 * <p>
 * No worker pool is created for operation execution. A queued asynchronous action can run on the
 * thread that grants admission, under the submitting caller's captured Kora context. A timer with
 * one virtual worker is created only for enabled bounded queues. Lifecycle shutdown rejects queued
 * and subsequent admission without releasing permits still owned by running operations.
 */
abstract class AbstractKoraBulkhead implements Bulkhead, Lifecycle {

    private static final Permit DISABLED_PERMIT = new Permit() {

        @Override
        public void observeError(Throwable error) {}

        @Override
        public void close() {}
    };
    // Completing an already-completed stage can synchronously release another permit.
    // Trampoline FIFO handoffs so a long queue cannot overflow the releasing thread's stack.
    private static final ThreadLocal<ArrayDeque<Runnable>> HANDOFFS = new ThreadLocal<>();

    private final String name;
    private final boolean enabled;
    private final int maximum;
    private final int queueCapacity;
    private final long maxWaitNanos;
    private final BulkheadTelemetry telemetry;
    private final LongSupplier clock;
    private final ReentrantLock lock = new ReentrantLock();
    private final ArrayDeque<Waiter> queue = new ArrayDeque<>();
    private final LongAdder rejected = new LongAdder();
    private final @Nullable ScheduledThreadPoolExecutor timer;
    // All writes hold lock; volatile allows inFlight() to expose a snapshot without locking.
    private volatile int inFlight;
    private volatile boolean closed;

    protected volatile int currentLimit;

    AbstractKoraBulkhead(String name, BulkheadConfig config, BulkheadTelemetry telemetry, LongSupplier clock, int initialLimit) {
        this.name = name;
        this.enabled = config.enabled();
        this.maximum = config.maxConcurrentCalls();
        this.queueCapacity = config.maxQueuedCalls();
        this.maxWaitNanos = config.maxWaitDuration().toNanos();
        this.currentLimit = initialLimit;
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
        this.clock = Objects.requireNonNull(clock, "clock");
        if (enabled && queueCapacity > 0) {
            this.timer = new ScheduledThreadPoolExecutor(
                1, Thread.ofVirtual().inheritInheritableThreadLocals(false).name("bulkhead-" + name + "-timer-", 0).factory()
            );
            timer.setRemoveOnCancelPolicy(true);
            timer.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        } else {
            this.timer = null;
        }
    }

    final void registerTelemetry() {
        telemetry.register(this);
    }

    /**
     * Called under the admission lock after an operation terminates.
     */
    protected abstract void sample(long now, long started, long duration, int concurrency, int admittedLimit, boolean failed);

    @Override
    public String name() {
        return name;
    }

    @Override
    public int maxConcurrentCalls() {
        return maximum;
    }

    @Override
    public int currentLimit() {
        return currentLimit;
    }

    @Override
    public int inFlight() {
        return inFlight;
    }

    @Override
    public long rejectedCalls() {
        return rejected.sum();
    }

    @Override
    public int queueLength() {
        lock.lock();
        try {
            return queue.size();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public @Nullable Permit tryAcquire() {
        try {
            return completed(request(false));
        } catch (BulkheadFullException error) {
            return null;
        }
    }

    @Override
    public Permit acquire() {
        var admission = request(true);
        try {
            return admission.get();
        } catch (InterruptedException error) {
            // If grant won the cancellation race, this caller owns and must return capacity.
            if (!admission.cancel(false)) {
                admission.whenComplete((permit, _) -> {
                    if (permit != null) {
                        permit.close();
                    }
                });
            }
            Thread.currentThread().interrupt();
            var rejected = new BulkheadFullException(name, Reason.INTERRUPTED);
            rejected.initCause(error);
            throw rejected;
        } catch (ExecutionException error) {
            return rethrow(error.getCause());
        }
    }

    @Override
    public CompletionStage<Permit> acquireAsync() {
        return request(true);
    }

    private CompletableFuture<Permit> request(boolean mayQueue) {
        if (!enabled && !closed) {
            return CompletableFuture.completedFuture(DISABLED_PERMIT);
        }
        var waiter = new Waiter(telemetry.observe(), clock.getAsLong());
        Reason rejection = null;
        boolean enqueued = false;
        lock.lock();
        try {
            if (closed) {
                rejection = Reason.SHUTDOWN;
            } else if (inFlight < currentLimit && queue.isEmpty()) {
                reserve(waiter);
            } else if (!mayQueue || queueCapacity == 0) {
                rejection = Reason.SATURATED;
            } else if (queue.size() >= queueCapacity) {
                rejection = Reason.QUEUE_FULL;
            } else {
                waiter.queued = true;
                queue.addLast(waiter);
                enqueued = true;
            }
        } finally {
            lock.unlock();
        }
        if (rejection != null) {
            var error = new BulkheadFullException(name, rejection);
            reject(waiter, error);
            throw error;
        }
        if (!enqueued) {
            grant(waiter);
        } else {
            waiter.future.whenComplete((_, _) -> {
                if (waiter.future.isCancelled()) {
                    cancelQueued(waiter);
                }
            });
            try {
                waiter.timeout = Objects.requireNonNull(timer)
                    .schedule(() -> expire(waiter), Math.max(0, maxWaitNanos - (clock.getAsLong() - waiter.started)), TimeUnit.NANOSECONDS);
                if (waiter.future.isDone()) {
                    waiter.timeout.cancel(false);
                }
            } catch (RejectedExecutionException error) {
                expireOrClose(waiter, Reason.SHUTDOWN);
            }
        }
        return waiter.future;
    }

    /**
     * Called with the admission lock held, including when draining queued callers.
     */
    @SuppressWarnings("NonAtomicOperationOnVolatileField")
    private void reserve(Waiter waiter) {
        waiter.concurrency = ++inFlight;
        waiter.admittedLimit = currentLimit;
    }

    private void grant(Waiter waiter) {
        cancelTimeout(waiter);
        var permit = new OwnedPermit(waiter);
        try {
            if (waiter.queued) {
                waiter.observation.recordQueueWait(Math.max(0, clock.getAsLong() - waiter.started));
            }
            waiter.observation.recordAcquire(true);
            if (!waiter.future.complete(permit)) {
                permit.observeError(new CancellationException("Bulkhead admission cancelled"));
                permit.close();
            }
        } catch (Throwable error) {
            try {
                permit.observeError(error);
            } finally {
                try {
                    permit.close();
                } finally {
                    waiter.future.completeExceptionally(error);
                }
            }
        }
    }

    private void reject(Waiter waiter, BulkheadFullException error) {
        cancelTimeout(waiter);
        rejected.increment();
        try {
            if (waiter.queued) {
                waiter.observation.recordQueueWait(Math.max(0, clock.getAsLong() - waiter.started));
            }
            waiter.observation.recordAcquire(false);
            waiter.observation.observeError(error);
        } finally {
            try {
                waiter.observation.end();
            } finally {
                waiter.future.completeExceptionally(error);
            }
        }
    }

    private void cancelQueued(Waiter waiter) {
        lock.lock();
        boolean removed;
        try {
            removed = queue.remove(waiter);
        } finally {
            lock.unlock();
        }
        if (removed) {
            cancelTimeout(waiter);
            try {
                waiter.observation.recordQueueWait(Math.max(0, clock.getAsLong() - waiter.started));
                waiter.observation.observeError(new CancellationException("Bulkhead admission cancelled"));
            } finally {
                waiter.observation.end();
            }
        }
    }

    private void expire(Waiter waiter) {
        expireOrClose(waiter, Reason.QUEUE_TIMEOUT);
    }

    private void expireOrClose(Waiter waiter, Reason reason) {
        lock.lock();
        boolean removed;
        try {
            removed = queue.remove(waiter);
        } finally {
            lock.unlock();
        }
        if (removed) {
            reject(waiter, new BulkheadFullException(name, reason));
        }
    }

    private static void cancelTimeout(Waiter waiter) {
        if (waiter.timeout != null) {
            waiter.timeout.cancel(false);
        }
    }

    private List<Runnable> drain() {
        var ready = new ArrayList<Runnable>();
        while (!closed && inFlight < currentLimit && !queue.isEmpty()) {
            var waiter = queue.removeFirst();
            if (clock.getAsLong() - waiter.started >= maxWaitNanos) {
                ready.add(() -> reject(waiter, new BulkheadFullException(name, Reason.QUEUE_TIMEOUT)));
            } else {
                reserve(waiter);
                ready.add(() -> grant(waiter));
            }
        }
        return ready;
    }

    private static void handoff(List<Runnable> ready) {
        var pending = HANDOFFS.get();
        if (pending != null) {
            pending.addAll(ready);
            return;
        }
        pending = new ArrayDeque<>(ready);
        HANDOFFS.set(pending);
        Throwable failure = null;
        try {
            while (!pending.isEmpty()) {
                try {
                    pending.removeFirst().run();
                } catch (Throwable error) {
                    if (failure == null) {
                        failure = error;
                    } else {
                        failure.addSuppressed(error);
                    }
                }
            }
        } finally {
            HANDOFFS.remove();
        }
        if (failure != null) {
            rethrow(failure);
        }
    }

    @Override
    public <T, E extends Throwable> CompletionStage<T> executeAsync(ThrowableCallable<? extends CompletionStage<T>, E> action) throws E {
        Objects.requireNonNull(action, "action");
        if (queueCapacity == 0 || !enabled) {
            return Bulkhead.super.executeAsync(action);
        }
        var context = captureContext();
        var admission = request(true);
        var result = new CompletableFuture<T>();
        result.whenComplete((_, _) -> {
            if (result.isCancelled()) {
                admission.cancel(false);
            }
        });
        admission.whenComplete((permit, error) -> {
            if (error != null) {
                result.completeExceptionally(error);
                return;
            }
            context.run(() -> {
                if (result.isCancelled()) {
                    permit.observeError(new CancellationException("Bulkhead execution cancelled before start"));
                    permit.close();
                    return;
                }
                try {
                    Objects.requireNonNull(action.call(), "action returned null stage").whenComplete((value, failure) -> context.run(() -> {
                        try {
                            try {
                                if (failure != null) {
                                    permit.observeError(failure);
                                }
                            } finally {
                                permit.close();
                            }
                            if (failure == null) {
                                result.complete(value);
                            } else {
                                result.completeExceptionally(failure);
                            }
                        } catch (Throwable observationFailure) {
                            result.completeExceptionally(observationFailure);
                        }
                    }));
                } catch (Throwable failure) {
                    try {
                        permit.observeError(failure);
                    } finally {
                        try {
                            permit.close();
                        } finally {
                            result.completeExceptionally(failure);
                        }
                    }
                }
            });
        });
        return result;
    }

    @Override
    public void init() {}

    /**
     * Captures submission context so queued execution cannot inherit the caller that frees capacity.
     */
    private static ScopedValue.Carrier captureContext() {
        var carrier = ScopedValue.where(OpentelemetryContext.VALUE, Context.current());
        // Bind absent values too, masking the context of a capacity-releasing caller.
        carrier = carrier.where(Observation.VALUE, Observation.VALUE.isBound() ? Observation.VALUE.get() : null);
        carrier = carrier.where(Principal.VALUE, Principal.current());
        carrier = carrier.where(MDC.VALUE, MDC.VALUE.isBound() ? MDC.VALUE.get().fork() : new MDC());
        return carrier;
    }

    /**
     * Stops admission and timers without releasing permits owned by running operations.
     */
    @Override
    public void release() {
        List<Waiter> waiting;
        lock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            waiting = List.copyOf(queue);
            queue.clear();
        } finally {
            lock.unlock();
        }
        try {
            handoff(
                waiting.stream().<Runnable>map(waiter -> () -> reject(waiter, new BulkheadFullException(name, Reason.SHUTDOWN))).toList()
            );
        } finally {
            if (timer != null) {
                timer.shutdownNow();
            }
        }
    }

    private static <T> T completed(CompletableFuture<T> future) {
        try {
            return future.join();
        } catch (CompletionException error) {
            return rethrow(error.getCause());
        }
    }

    @SuppressWarnings("unchecked")
    private static <T, E extends Throwable> T rethrow(Throwable error) throws E {
        throw (E) error;
    }

    private static final class Waiter {

        final BulkheadObservation observation;
        final long started;
        final CompletableFuture<Permit> future = new CompletableFuture<>();
        volatile @Nullable ScheduledFuture<?> timeout;
        boolean queued;
        int concurrency;
        int admittedLimit;

        Waiter(BulkheadObservation observation, long started) {
            this.observation = observation;
            this.started = started;
        }
    }

    private final class OwnedPermit implements Permit {

        private final AtomicBoolean ended = new AtomicBoolean();
        private final Waiter waiter;
        private final long started = clock.getAsLong();
        private volatile boolean failed;
        private volatile boolean ignored;

        OwnedPermit(Waiter waiter) {
            this.waiter = waiter;
        }

        @Override
        public void observeError(Throwable error) {
            Throwable cause = error;
            while (cause instanceof CompletionException || cause instanceof ExecutionException) {
                cause = cause.getCause();
            }
            ignored = cause instanceof CancellationException || cause instanceof NonCircuitableException
                    || cause instanceof NonRetryableException;
            failed = !ignored;
            waiter.observation.observeError(error);
        }

        @Override
        @SuppressWarnings("NonAtomicOperationOnVolatileField") // The admission lock protects the decrement.
        public void close() {
            if (!ended.compareAndSet(false, true)) {
                return;
            }
            List<Runnable> ready;
            lock.lock();
            try {
                long now = clock.getAsLong();
                if (!ignored) {
                    sample(now, started, Math.max(0, now - started), waiter.concurrency, waiter.admittedLimit, failed);
                }
                inFlight--;
                ready = drain();
            } finally {
                lock.unlock();
            }
            try {
                waiter.observation.end();
            } finally {
                handoff(ready);
            }
        }
    }
}
