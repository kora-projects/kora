package io.koraframework.common.executor;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Executes every task in a fresh virtual thread while limiting the maximum
 * number of concurrently executing tasks.
 *
 * <p>Unlike {@link java.util.concurrent.ThreadPoolExecutor}, this executor
 * does not reuse worker threads. Every started task gets a new virtual thread.
 *
 * <p>Additional accepted tasks wait in an unbounded FIFO queue until execution
 * capacity becomes available.
 *
 * <h2>Lifecycle semantics</h2>
 *
 * <ul>
 *     <li>{@link #shutdown()} stops accepting new tasks and drains all
 *     previously accepted tasks.</li>
 *     <li>{@link #shutdownNow()} removes queued tasks and interrupts all
 *     currently running virtual threads.</li>
 *     <li>The executor reaches the terminated state only after every running
 *     virtual thread has actually completed.</li>
 * </ul>
 */
public final class BoundedVirtualThreadQueuedPerTaskExecutor extends AbstractExecutorService {

    private enum State {
        RUNNING,
        SHUTDOWN,
        STOP,
        TERMINATED
    }

    private final int parallelism;
    private final ThreadFactory threadFactory;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition termination = this.lock.newCondition();

    /**
     * Accepted but not yet started tasks.
     * <p>
     * Guarded by {@link #lock}.
     */
    private final ArrayDeque<Runnable> queue = new ArrayDeque<>();

    /**
     * Threads that have been successfully started and have not yet completed.
     * <p>
     * Guarded by {@link #lock}.
     */
    private final Set<Thread> runningThreads = new HashSet<>();

    /**
     * Guarded by {@link #lock}.
     */
    private State state = State.RUNNING;

    public BoundedVirtualThreadQueuedPerTaskExecutor(int parallelism) {
        this(parallelism, Thread.ofVirtual().name("bounded-q-vt-executor-", 0));
    }

    public BoundedVirtualThreadQueuedPerTaskExecutor(int parallelism, String threadPoolName) {
        this(parallelism, virtualThreadFactoryBuilder(threadPoolName));
    }

    public BoundedVirtualThreadQueuedPerTaskExecutor(int parallelism, Thread.Builder.OfVirtual virtualThreadFactoryBuilder) {
        if (parallelism <= 0) {
            throw new IllegalArgumentException("parallelism must be greater than 0, but was " + parallelism);
        }

        this.parallelism = parallelism;
        this.threadFactory = Objects.requireNonNull(virtualThreadFactoryBuilder, "threadFactory").factory();
    }

    /**
     * Returns the configured maximum number of concurrently executing tasks.
     */
    private int parallelism() {
        return this.parallelism;
    }

    /**
     * Returns the number of tasks currently executing in virtual threads.
     */
    private int runningTaskCount() {
        this.lock.lock();
        try {
            return this.runningThreads.size();
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * Returns the number of accepted tasks waiting for execution.
     */
    private int queuedTaskCount() {
        this.lock.lock();
        try {
            return this.queue.size();
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public void execute(Runnable command) {
        Objects.requireNonNull(command, "command");

        this.lock.lock();
        try {
            ensureRunningLocked();

            if (this.runningThreads.size() < this.parallelism) {
                /*
                 * Thread creation, registration and start are performed under
                 * the lifecycle lock.
                 *
                 * This deliberately makes acceptance atomic relative to
                 * shutdownNow(): after execute() returns successfully, the
                 * task is either:
                 *
                 * 1. stored in queue; or
                 * 2. represented by a started and tracked virtual thread.
                 *
                 * There is no externally observable untracked STARTING state.
                 */
                startTaskLocked(command);
            } else {
                this.queue.addLast(command);
            }
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public void shutdown() {
        this.lock.lock();
        try {
            if (this.state != State.RUNNING) {
                return;
            }

            this.state = State.SHUTDOWN;
            tryTerminateLocked();
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public List<Runnable> shutdownNow() {
        final List<Runnable> notStarted;
        final List<Thread> threadsToInterrupt;

        this.lock.lock();
        try {
            if (this.state == State.TERMINATED) {
                return List.of();
            }

            this.state = State.STOP;

            notStarted = new ArrayList<>(this.queue);
            this.queue.clear();

            threadsToInterrupt = new ArrayList<>(this.runningThreads);

            tryTerminateLocked();
        } finally {
            this.lock.unlock();
        }

        /*
         * Interrupt outside the lifecycle lock. Interrupted tasks may finish
         * immediately and enter taskCompleted(), which needs the same lock.
         */
        interruptAll(threadsToInterrupt);

        return notStarted;
    }

    @Override
    public boolean isShutdown() {
        this.lock.lock();
        try {
            return this.state != State.RUNNING;
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public boolean isTerminated() {
        this.lock.lock();
        try {
            return this.state == State.TERMINATED;
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        Objects.requireNonNull(unit, "unit");

        long remainingNanos = unit.toNanos(timeout);

        this.lock.lockInterruptibly();
        try {
            while (this.state != State.TERMINATED) {
                if (remainingNanos <= 0L) {
                    return false;
                }

                remainingNanos = this.termination.awaitNanos(remainingNanos);
            }

            return true;
        } finally {
            this.lock.unlock();
        }
    }

    /**
     * Prevents the default ExecutorService.close() behavior from deadlocking
     * when close() is accidentally invoked from one of this executor's tasks.
     */
    @Override
    public void close() {
        this.lock.lock();
        try {
            if (this.runningThreads.contains(Thread.currentThread())) {
                throw new IllegalStateException("The executor cannot be closed from one of its own tasks");
            }
        } finally {
            this.lock.unlock();
        }

        shutdown();

        boolean interrupted = false;

        for (; ; ) {
            try {
                if (awaitTermination(1L, TimeUnit.HOURS)) {
                    break;
                }
            } catch (InterruptedException e) {
                if (!interrupted) {
                    /*
                     * Match the intended ExecutorService.close() behavior:
                     * interruption escalates graceful shutdown to an immediate
                     * shutdown attempt, but close still waits for termination.
                     */
                    interrupted = true;
                    shutdownNow();
                }
            }
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Starts a new virtual thread for the supplied command.
     *
     * <p>Must be invoked while holding {@link #lock}.
     *
     * <p>If thread creation or start fails, the task is not considered
     * accepted: execute() propagates the failure to its caller.
     */
    private void startTaskLocked(Runnable command) {
        assert this.lock.isHeldByCurrentThread();
        assert this.state == State.RUNNING
            || this.state == State.SHUTDOWN;
        assert this.runningThreads.size() < this.parallelism;

        final Thread thread;

        try {
            thread = this.threadFactory.newThread(() -> runTask(command));
        } catch (RuntimeException | Error e) {
            throw e;
        }

        if (thread == null) {
            throw new RejectedExecutionException("ThreadFactory returned null");
        } else if (thread.getState() != Thread.State.NEW) {
            throw new RejectedExecutionException("ThreadFactory returned an already started thread: " + thread);
        } else if (!this.runningThreads.add(thread)) {
            throw new RejectedExecutionException("ThreadFactory returned the same Thread instance more than once: " + thread);
        }

        try {
            thread.start();
        } catch (RuntimeException | Error e) {
            this.runningThreads.remove(thread);
            tryTerminateLocked();
            throw e;
        }
    }

    private void runTask(Runnable command) {
        try {
            command.run();
        } finally {
            taskCompleted(Thread.currentThread());
        }
    }

    private void taskCompleted(Thread completedThread) {
        List<Thread> threadsToInterrupt = List.of();
        Throwable dispatchFailure = null;

        this.lock.lock();
        try {
            if (!this.runningThreads.remove(completedThread)) {
                /*
                 * Do not throw from the task-finalization path. Doing so could
                 * mask the user's original task exception.
                 *
                 * This condition means executor bookkeeping was corrupted,
                 * so force the executor into STOP.
                 */
                dispatchFailure = new IllegalStateException("Completed virtual thread was not tracked: " + completedThread);

                threadsToInterrupt = transitionToStopLocked(dispatchFailure, null);
            } else if (this.state != State.STOP) {
                try {
                    dispatchAvailableLocked();
                } catch (RuntimeException | Error e) {
                    dispatchFailure = e;
                    threadsToInterrupt = transitionToStopLocked(e, null);
                }
            }

            tryTerminateLocked();
        } finally {
            this.lock.unlock();
        }

        interruptAll(threadsToInterrupt);

        if (dispatchFailure != null) {
            reportUncaught(dispatchFailure);
        }
    }

    /**
     * Starts queued tasks until all execution slots are occupied or the queue
     * becomes empty.
     *
     * <p>This is called after a running task completes. Tasks processed here
     * have already been accepted, so inability to create a virtual thread
     * cannot be reported back to their original submitters.
     *
     * <p>The caller therefore converts such a failure into terminal STOP,
     * cancels all accepted but unstarted Future tasks, and interrupts remaining
     * running tasks.
     */
    private void dispatchAvailableLocked() {
        assert this.lock.isHeldByCurrentThread();

        while (this.state != State.STOP
                && this.state != State.TERMINATED
                && this.runningThreads.size() < this.parallelism
                && !this.queue.isEmpty()) {
            var command = this.queue.removeFirst();

            try {
                startTaskLocked(command);
            } catch (RuntimeException | Error e) {
                /*
                 * Preserve the accepted task so the terminal failure path can
                 * cancel it together with all other queued tasks.
                 */
                this.queue.addFirst(command);
                throw e;
            }
        }
    }

    /**
     * Converts the executor to STOP after an internal fatal failure.
     *
     * <p>Tasks already accepted by this executor can no longer be reliably
     * executed if creation of a replacement virtual thread fails. Queued
     * Future tasks are cancelled so callers waiting on their futures are not
     * left blocked indefinitely.
     *
     * <p>Raw Runnable tasks have no standard completion channel; they are
     * discarded after the failure is reported through the uncaught exception
     * handler.
     *
     * <p>Must be invoked while holding {@link #lock}.
     */
    private List<Thread> transitionToStopLocked(Throwable failure, Runnable additionalUnstartedTask) {
        assert this.lock.isHeldByCurrentThread();
        Objects.requireNonNull(failure, "failure");

        if (this.state == State.TERMINATED) {
            return List.of();
        }

        this.state = State.STOP;

        if (additionalUnstartedTask != null) {
            cancelIfFuture(additionalUnstartedTask);
        }

        while (!this.queue.isEmpty()) {
            cancelIfFuture(this.queue.removeFirst());
        }

        var threadsToInterrupt = new ArrayList<>(this.runningThreads);

        tryTerminateLocked();

        return threadsToInterrupt;
    }

    /**
     * Moves to TERMINATED only when shutdown has started and no accepted work
     * remains active.
     *
     * <p>Must be invoked while holding {@link #lock}.
     */
    private void tryTerminateLocked() {
        assert this.lock.isHeldByCurrentThread();

        if (this.state == State.RUNNING || this.state == State.TERMINATED) {
            return;
        }

        if (!this.runningThreads.isEmpty()) {
            return;
        }

        if (this.state == State.SHUTDOWN && !this.queue.isEmpty()) {
            /*
             * Under normal operation this condition is temporary: a running
             * task will complete and dispatch the next queued task.
             */
            return;
        }

        this.state = State.TERMINATED;
        this.termination.signalAll();
    }

    private void ensureRunningLocked() {
        assert this.lock.isHeldByCurrentThread();

        if (this.state != State.RUNNING) {
            throw new RejectedExecutionException(
                "Executor is not accepting new tasks; state=" + this.state
            );
        }
    }

    private static void cancelIfFuture(Runnable task) {
        if (task instanceof Future<?> future) {
            future.cancel(false);
        }
    }

    private static void interruptAll(List<Thread> threads) {
        for (var thread : threads) {
            try {
                thread.interrupt();
            } catch (RuntimeException ignored) {
                /*
                 * Thread.interrupt() normally does not fail. A malformed custom
                 * Thread implementation must not prevent attempts to interrupt
                 * the remaining tasks.
                 */
            }
        }
    }

    private static void reportUncaught(Throwable failure) {
        var currentThread = Thread.currentThread();
        var handler = currentThread.getUncaughtExceptionHandler();

        if (handler != null) {
            try {
                handler.uncaughtException(currentThread, failure);
            } catch (Throwable ignored) {
                /*
                 * UncaughtExceptionHandler failures must not corrupt executor
                 * lifecycle bookkeeping.
                 */
            }
        }
    }

    private static Thread.Builder.OfVirtual virtualThreadFactoryBuilder(String threadPoolName) {
        Objects.requireNonNull(threadPoolName, "threadPoolName");
        return Thread.ofVirtual().name(threadPoolName.endsWith("-") ? threadPoolName : threadPoolName + "-", 0);
    }
}
