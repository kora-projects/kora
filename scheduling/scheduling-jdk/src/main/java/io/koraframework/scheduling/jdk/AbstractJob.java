package io.koraframework.scheduling.jdk;

import io.koraframework.application.graph.Lifecycle;
import io.koraframework.common.telemetry.Observation;
import io.koraframework.common.telemetry.OpentelemetryContext;
import io.koraframework.common.util.TimeUtils;
import io.koraframework.scheduling.common.telemetry.SchedulingTelemetry;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;

public abstract class AbstractJob implements Lifecycle {

    private final Logger logger;

    private final ReentrantLock lock = new ReentrantLock(true);
    private final ReentrantLock executionLock = new ReentrantLock(true);

    private final SchedulingTelemetry telemetry;
    private final SchedulingJdkExecutor service;
    private final Runnable command;

    private volatile boolean started = false;
    private volatile @Nullable ScheduledFuture<?> scheduledFuture;
    private long generation;

    public AbstractJob(SchedulingTelemetry telemetry, SchedulingJdkExecutor service, Runnable command) {
        this.logger = LoggerFactory.getLogger(telemetry.jobClass());
        this.telemetry = telemetry;
        this.service = service;
        this.command = command;
    }

    /**
     * Schedules an execution, or returns {@code null} if no future execution exists.
     */
    protected abstract @Nullable ScheduledFuture<?> schedule(SchedulingJdkExecutor service, Runnable command);

    /**
     * Whether to schedule another execution after the current execution completes.
     * Executors handle repetition themselves for fixed-rate and fixed-delay jobs.
     */
    protected boolean rescheduleAfterRun() {
        return false;
    }

    // Called under lock so release() always cancels the latest scheduled execution.
    private void scheduleNext(long generation) {
        try {
            this.scheduledFuture = this.schedule(this.service, () -> this.runJob(generation));
        } catch (RuntimeException | Error e) {
            this.started = false;
            this.scheduledFuture = null;
            throw e;
        }
        if (this.scheduledFuture == null) {
            this.started = false;
            logger.warn("JDK Job '{}#{}' won't be scheduled because it has no next fire time",
                telemetry.jobClass().getCanonicalName(), telemetry.jobMethod());
        }
    }

    @Override
    public final void init() {
        this.lock.lock();
        try {
            if (this.started) {
                return;
            }
            this.started = true;
            logger.debug("JDK Job '{}#{}' starting...", telemetry.jobClass().getCanonicalName(), telemetry.jobMethod());
            final long started = TimeUtils.started();

            this.scheduleNext(++this.generation);

            if (this.started) {
                logger.info("JDK Job '{}#{}' started in {}", telemetry.jobClass().getCanonicalName(), telemetry.jobMethod(),
                    TimeUtils.tookForLogging(started));
            }
        } finally {
            this.lock.unlock();
        }
    }

    private void runJob(long generation) {
        // Serialize executions across release/init without blocking lifecycle operations.
        try {
            this.executionLock.lockInterruptibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        try {
            this.lock.lock();
            try {
                if (!this.started || this.generation != generation) {
                    return;
                }
            } finally {
                this.lock.unlock();
            }
            ScopedValue.where(io.koraframework.logging.common.MDC.VALUE, new io.koraframework.logging.common.MDC())
                .where(OpentelemetryContext.VALUE, io.opentelemetry.context.Context.root())
                .run(() -> {
                    MDC.clear();
                    var observation = this.telemetry.observe();
                    ScopedValue.where(Observation.VALUE, observation)
                        .where(OpentelemetryContext.VALUE, io.opentelemetry.context.Context.root().with(observation.span()))
                        .run(() -> {
                            observation.observeRun();
                            try {
                                this.command.run();
                            } catch (Throwable e) {
                                observation.observeError(e);
                            } finally {
                                observation.end();
                            }
                        });
                });
            this.lock.lock();
            try {
                if (this.started && this.generation == generation && this.rescheduleAfterRun()) {
                    this.scheduleNext(generation);
                }
            } finally {
                this.lock.unlock();
            }
        } finally {
            this.executionLock.unlock();
        }
    }

    @Override
    public final void release() {
        // Running work is drained by the executor using its shared shutdown deadline.
        logger.debug("JDK Job '{}#{}' stopping...", telemetry.jobClass().getCanonicalName(), telemetry.jobMethod());
        final long started = TimeUtils.started();

        this.lock.lock();
        try {
            if (!this.started) {
                return;
            }
            this.started = false;

            var f = this.scheduledFuture;
            this.scheduledFuture = null;
            if (f != null) {
                f.cancel(false);
            }

            logger.info("JDK Job '{}#{}' stopped in {}", telemetry.jobClass().getCanonicalName(), telemetry.jobMethod(), TimeUtils.tookForLogging(started));
        } finally {
            this.lock.unlock();
        }
    }
}
