package io.koraframework.common.executor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Starts each accepted task in a new virtual thread after execution capacity
 * becomes available.
 *
 * <p>Submitting a task waits for a fair semaphore permit before the virtual
 * thread is created. This applies backpressure to the submitting thread and
 * avoids creating parked virtual threads for work that cannot run yet.
 */
public final class BoundedVirtualThreadBlockingPerTaskExecutor extends AbstractExecutorService {

    private final Semaphore semaphore;
    private final ThreadFactory threadFactory;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition termination = this.lock.newCondition();
    private final Set<Thread> threads = new HashSet<>();

    private State state = State.RUNNING;

    public BoundedVirtualThreadBlockingPerTaskExecutor(int parallelism) {
        this(parallelism, Thread.ofVirtual().name("bounded-b-vt-executor-", 0));
    }

    public BoundedVirtualThreadBlockingPerTaskExecutor(int parallelism, String threadPoolName) {
        this(parallelism, virtualThreadFactoryBuilder(threadPoolName));
    }

    public BoundedVirtualThreadBlockingPerTaskExecutor(int parallelism, Thread.Builder.OfVirtual virtualThreadFactoryBuilder) {
        if (parallelism <= 0) {
            throw new IllegalArgumentException("parallelism must be greater than 0, but was " + parallelism);
        }

        this.semaphore = new Semaphore(parallelism, true);
        this.threadFactory = Objects.requireNonNull(virtualThreadFactoryBuilder, "virtualThreadFactoryBuilder").factory();
    }

    @Override
    public void execute(Runnable command) {
        Objects.requireNonNull(command, "command");

        acquirePermit();

        var started = false;
        try {
            this.lock.lock();
            try {
                ensureRunningLocked();

                var thread = newThread(command);
                if (!this.threads.add(thread)) {
                    throw new RejectedExecutionException("ThreadFactory returned the same Thread instance more than once: " + thread);
                }
                try {
                    thread.start();
                    started = true;
                } catch (RuntimeException | Error e) {
                    this.threads.remove(thread);
                    tryTerminateLocked();
                    throw e;
                }
            } finally {
                this.lock.unlock();
            }
        } finally {
            if (!started) {
                this.semaphore.release();
            }
        }
    }

    @Override
    public void shutdown() {
        this.lock.lock();
        try {
            if (this.state == State.RUNNING) {
                this.state = State.SHUTDOWN;
                tryTerminateLocked();
            }
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public List<Runnable> shutdownNow() {
        List<Thread> threadsToInterrupt;
        this.lock.lock();
        try {
            if (this.state == State.TERMINATED) {
                return List.of();
            }

            this.state = State.STOP;
            threadsToInterrupt = new ArrayList<>(this.threads);
            tryTerminateLocked();
        } finally {
            this.lock.unlock();
        }

        interruptAll(threadsToInterrupt);
        return List.of();
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

        var remainingNanos = unit.toNanos(timeout);
        this.lock.lockInterruptibly();
        try {
            while (this.state != State.TERMINATED) {
                if (remainingNanos <= 0) {
                    return false;
                }
                remainingNanos = this.termination.awaitNanos(remainingNanos);
            }
            return true;
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public void close() {
        this.lock.lock();
        try {
            if (this.threads.contains(Thread.currentThread())) {
                throw new IllegalStateException("The executor cannot be closed from one of its own tasks");
            }
        } finally {
            this.lock.unlock();
        }

        super.close();
    }

    private Thread newThread(Runnable command) {
        var thread = this.threadFactory.newThread(() -> runTask(command));
        if (thread == null) {
            throw new RejectedExecutionException("ThreadFactory returned null");
        }
        if (thread.getState() != Thread.State.NEW) {
            throw new RejectedExecutionException("ThreadFactory returned an already started thread: " + thread);
        }
        return thread;
    }

    private void runTask(Runnable command) {
        try {
            if (!isStopped()) {
                command.run();
            }
        } finally {
            taskCompleted(Thread.currentThread());
            this.semaphore.release();
        }
    }

    private void acquirePermit() {
        try {
            while (true) {
                this.lock.lock();
                try {
                    ensureRunningLocked();
                } finally {
                    this.lock.unlock();
                }

                if (this.semaphore.tryAcquire(100, TimeUnit.MILLISECONDS)) {
                    return;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RejectedExecutionException("Interrupted while waiting for execution permit", e);
        }
    }

    private boolean isStopped() {
        this.lock.lock();
        try {
            return this.state == State.STOP || this.state == State.TERMINATED;
        } finally {
            this.lock.unlock();
        }
    }

    private void taskCompleted(Thread thread) {
        this.lock.lock();
        try {
            this.threads.remove(thread);
            tryTerminateLocked();
        } finally {
            this.lock.unlock();
        }
    }

    private void ensureRunningLocked() {
        if (this.state != State.RUNNING) {
            throw new RejectedExecutionException("Executor is not accepting new tasks; state=" + this.state);
        }
    }

    private void tryTerminateLocked() {
        if (this.state == State.RUNNING || this.state == State.TERMINATED || !this.threads.isEmpty()) {
            return;
        }

        this.state = State.TERMINATED;
        this.termination.signalAll();
    }

    private static void interruptAll(List<Thread> threads) {
        for (var thread : threads) {
            thread.interrupt();
        }
    }

    private static Thread.Builder.OfVirtual virtualThreadFactoryBuilder(String threadPoolName) {
        Objects.requireNonNull(threadPoolName, "threadPoolName");
        return Thread.ofVirtual().name(threadPoolName.endsWith("-") ? threadPoolName : threadPoolName + "-", 0);
    }

    private enum State {
        RUNNING,
        SHUTDOWN,
        STOP,
        TERMINATED
    }
}
