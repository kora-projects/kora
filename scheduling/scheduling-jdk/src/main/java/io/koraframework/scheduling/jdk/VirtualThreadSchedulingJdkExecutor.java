package io.koraframework.scheduling.jdk;

import io.koraframework.application.graph.Lifecycle;
import io.koraframework.common.executor.LimitedVirtualThreadPerTaskExecutor;
import io.koraframework.common.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Uses one platform thread for timers and a fresh virtual thread for each job
 * execution. Concurrent job executions are limited by
 * {@link SchedulingJdkConfig#maxConcurrentExecutions()}.
 *
 * <p>Periodic executions never overlap. Fixed-rate executions retain their
 * original schedule, while fixed-delay executions are scheduled after the
 * preceding execution completes.
 */
public final class VirtualThreadSchedulingJdkExecutor implements Lifecycle, SchedulingJdkExecutor {

    private static final Logger logger = LoggerFactory.getLogger(VirtualThreadSchedulingJdkExecutor.class);

    private final SchedulingJdkConfig config;
    private final int parallelism;
    private final ReentrantLock lock = new ReentrantLock();
    // Lifecycle state, outstanding tasks and timer registrations are guarded by lock.
    private final Set<ScheduledTask> tasks = new HashSet<>();

    private ScheduledThreadPoolExecutor timer;
    private LimitedVirtualThreadPerTaskExecutor workers;
    private boolean accepting;
    private long sequence;

    public VirtualThreadSchedulingJdkExecutor(SchedulingJdkConfig config) {
        this.config = Objects.requireNonNull(config);
        this.parallelism = Math.max(1, config.maxConcurrentExecutions());
    }

    @Override
    public void init() {
        this.lock.lock();
        try {
            if (this.timer != null) {
                throw new IllegalStateException("SchedulingJdkExecutor has already been initialized");
            }
            var started = System.nanoTime();
            this.workers = new LimitedVirtualThreadPerTaskExecutor(this.parallelism, "kora-jdk-scheduler-job");
            this.timer = new ScheduledThreadPoolExecutor(1, Thread.ofPlatform()
                    .name("kora-jdk-scheduler-timer")
                    .daemon(false)
                    .inheritInheritableThreadLocals(false)
                    .factory());
            this.timer.setRemoveOnCancelPolicy(true);
            this.accepting = true;
            logger.info("SchedulingJdkExecutor started in {}", TimeUtils.tookForLogging(started));
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public void release() {
        this.lock.lock();
        try {
            if (this.timer == null) {
                return;
            }
            this.accepting = false;
            // Let running jobs finish, but stop all future periodic executions.
            for (var task : List.copyOf(this.tasks)) {
                if (task.period != 0) {
                    task.cancel(false);
                }
            }
            this.timer.shutdown();
        } finally {
            this.lock.unlock();
        }

        var started = System.nanoTime();
        var timeout = this.config.shutdownWait().toNanos();
        try {
            // Delayed one-shot jobs may still be dispatched during graceful shutdown.
            var terminated = this.timer.awaitTermination(timeout, TimeUnit.NANOSECONDS);
            this.workers.shutdown();
            terminated = terminated && this.workers.awaitTermination(
                Math.max(0, timeout - (System.nanoTime() - started)), TimeUnit.NANOSECONDS);
            if (!terminated) {
                shutdownNow();
                logger.warn("SchedulingJdkExecutor failed completing graceful shutdown in {}", this.config.shutdownWait());
            }
        } catch (InterruptedException e) {
            shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("SchedulingJdkExecutor stopped in {}", TimeUtils.tookForLogging(started));
    }

    private void shutdownNow() {
        this.lock.lock();
        try {
            this.timer.shutdownNow();
            // Cancel the actual job futures, including jobs already queued in workers.
            for (var task : List.copyOf(this.tasks)) {
                task.cancel(true);
            }
        } finally {
            this.lock.unlock();
        }
        this.workers.shutdownNow();
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable job, long initialDelay, long delay, TimeUnit timeUnit) {
        if (delay <= 0) {
            throw new IllegalArgumentException("delay must be greater than 0");
        }
        return schedule(job, initialDelay, -timeUnit.toNanos(delay), timeUnit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable job, long initialDelay, long period, TimeUnit timeUnit) {
        if (period <= 0) {
            throw new IllegalArgumentException("period must be greater than 0");
        }
        return schedule(job, initialDelay, timeUnit.toNanos(period), timeUnit);
    }

    @Override
    public ScheduledFuture<?> scheduleOnce(Runnable job, long delay, TimeUnit timeUnit) {
        return schedule(job, delay, 0, timeUnit);
    }

    private ScheduledFuture<?> schedule(Runnable job, long delay, long period, TimeUnit unit) {
        Objects.requireNonNull(job, "job");
        Objects.requireNonNull(unit, "timeUnit");
        this.lock.lock();
        try {
            if (!this.accepting) {
                throw new RejectedExecutionException("SchedulingJdkExecutor is not accepting new tasks");
            }
            var task = new ScheduledTask(job, period, this.sequence++);
            this.tasks.add(task);
            task.scheduleAfter(unit.toNanos(Math.max(0, delay)));
            return task;
        } finally {
            this.lock.unlock();
        }
    }

    private final class ScheduledTask extends FutureTask<Void> implements ScheduledFuture<Void> {
        // Positive for fixed rate, negative for fixed delay, zero for one-shot.
        private final long period;
        private final long sequence;
        private volatile long deadline;
        private ScheduledFuture<?> trigger;

        private ScheduledTask(Runnable command, long period, long sequence) {
            super(command, null);
            this.period = period;
            this.sequence = sequence;
        }

        // Called under the lifecycle lock, including registration of the next trigger.
        private void scheduleAfter(long delay) {
            this.deadline = System.nanoTime() + delay;
            registerTrigger(delay);
        }

        private void registerTrigger(long delay) {
            try {
                this.trigger = timer.schedule(this::dispatch, delay, TimeUnit.NANOSECONDS);
            } catch (RuntimeException | Error e) {
                setException(e);
                throw e;
            }
        }

        private void dispatch() {
            if (!isDone()) {
                try {
                    workers.execute(this);
                } catch (RuntimeException | Error e) {
                    setException(e);
                }
            }
        }

        @Override
        public void run() {
            if (this.period == 0) {
                super.run();
            } else if (super.runAndReset()) {
                lock.lock();
                try {
                    if (!accepting) {
                        cancel(false);
                    } else if (!isDone()) {
                        if (this.period > 0) {
                            this.deadline += this.period;
                            registerTrigger(this.deadline - System.nanoTime());
                        } else {
                            scheduleAfter(-this.period);
                        }
                    }
                } finally {
                    lock.unlock();
                }
            }
        }

        @Override
        protected void done() {
            lock.lock();
            try {
                tasks.remove(this);
                if (this.trigger != null) {
                    this.trigger.cancel(false);
                }
            } finally {
                lock.unlock();
            }
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(this.deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            if (this == other) {
                return 0;
            }
            if (other instanceof ScheduledTask task) {
                var difference = this.deadline - task.deadline;
                return difference == 0 ? Long.compare(this.sequence, task.sequence) : Long.compare(difference, 0);
            }
            return Long.compare(getDelay(TimeUnit.NANOSECONDS), other.getDelay(TimeUnit.NANOSECONDS));
        }
    }
}
