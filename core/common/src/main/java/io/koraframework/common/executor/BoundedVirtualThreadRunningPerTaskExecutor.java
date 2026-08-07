package io.koraframework.common.executor;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Starts each accepted task in a new virtual thread and limits concurrently
 * executing task bodies with a fair semaphore.
 *
 * <p>Submitting a task does not wait for execution capacity. If all permits
 * are busy, the virtual thread is started and parked on the semaphore until
 * capacity becomes available.
 */
public final class BoundedVirtualThreadRunningPerTaskExecutor extends AbstractExecutorService {

    private final Semaphore semaphore;
    private final ThreadFactory threadFactory;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition termination = this.lock.newCondition();
    private final Set<Thread> threads = new HashSet<>();

    private State state = State.RUNNING;

    public BoundedVirtualThreadRunningPerTaskExecutor(int parallelism) {
        this(parallelism, Thread.ofVirtual().name("bounded-r-vt-executor-", 0));
    }

    public BoundedVirtualThreadRunningPerTaskExecutor(int parallelism, String threadPoolName) {
        this(parallelism, virtualThreadFactoryBuilder(threadPoolName));
    }

    public BoundedVirtualThreadRunningPerTaskExecutor(int parallelism, Thread.Builder.OfVirtual virtualThreadFactoryBuilder) {
        if (parallelism <= 0) {
            throw new IllegalArgumentException("parallelism must be greater than 0, but was " + parallelism);
        }

        this.semaphore = new Semaphore(parallelism, true);
        this.threadFactory = Objects.requireNonNull(virtualThreadFactoryBuilder, "virtualThreadFactoryBuilder").factory();
    }

    @Override
    public void execute(Runnable command) {
        Objects.requireNonNull(command, "command");

        this.lock.lock();
        try {
            ensureRunningLocked();

            var thread = newThread(command);
            if (!this.threads.add(thread)) {
                throw new RejectedExecutionException("ThreadFactory returned the same Thread instance more than once: " + thread);
            }
            try {
                thread.start();
            } catch (RuntimeException | Error e) {
                this.threads.remove(thread);
                tryTerminateLocked();
                throw e;
            }
        } finally {
            this.lock.unlock();
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
        var acquired = false;
        try {
            this.semaphore.acquire();
            acquired = true;

            if (!isStopped()) {
                command.run();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (acquired) {
                this.semaphore.release();
            }
            taskCompleted(Thread.currentThread());
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
