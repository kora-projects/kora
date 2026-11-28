package io.koraframework.scheduling.jdk.job;

import io.koraframework.application.graph.*;
import io.koraframework.common.context.*;
import io.koraframework.common.telemetry.*;
import io.koraframework.common.util.*;
import io.koraframework.scheduling.common.telemetry.*;
import io.koraframework.scheduling.jdk.*;
import org.jspecify.annotations.*;
import org.slf4j.*;

import java.util.concurrent.*;
import java.util.concurrent.locks.*;
import java.util.function.*;

public abstract class KoraJdkJob implements Lifecycle {

    private final Logger logger;

    private final ReentrantLock lock = new ReentrantLock(true);
    private final ReentrantLock executionLock = new ReentrantLock(true);

    private final SchedulingTelemetry telemetry;
    private final SchedulingJdkExecutor service;
    private final Runnable command;
    private final boolean enabled;

    private volatile boolean started = false;
    private volatile @Nullable ScheduledFuture<?> scheduledFuture;
    private long generation;

    public KoraJdkJob(SchedulingTelemetry telemetry, SchedulingJdkExecutor service, Runnable command) {
        this(telemetry, service, command, true);
    }

    /**
     * @param enabled {@code false} when the job is disabled by the {@code enabled} key of its configuration and is never scheduled
     */
    public KoraJdkJob(SchedulingTelemetry telemetry, SchedulingJdkExecutor service, Runnable command, boolean enabled) {
        this.logger = LoggerFactory.getLogger(telemetry.jobClass());
        this.telemetry = telemetry;
        this.service = service;
        this.command = command;
        this.enabled = enabled;
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

    private void scheduleNext(long generation) {
        try {
            this.scheduledFuture = this.schedule(this.service, new JdkJobScheduleRunner(this, generation));
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
        if (!this.enabled) {
            logger.info("JDK Job '{}#{}' is disabled by configuration and won't be scheduled", telemetry.jobClass().getCanonicalName(), telemetry.jobMethod());
            return;
        }
        this.lock.lock();
        try {
            if (this.started) {
                return;
            }
            this.started = true;
            logger.debug("JDK Job '{}#{}' starting...", telemetry.jobClass().getCanonicalName(), telemetry.jobMethod());
            final long startedTime = TimeUtils.started();

            this.scheduleNext(++this.generation);

            if (this.started) {
                logger.info("JDK Job '{}#{}' started in {}", telemetry.jobClass().getCanonicalName(), telemetry.jobMethod(),
                    TimeUtils.tookForLogging(startedTime));
            }
        } finally {
            this.lock.unlock();
        }
    }

    private void runJob(long generation) {
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

            MDC.clear();
            var observation = this.telemetry.observe();
            var rootOtelCtx = io.opentelemetry.context.Context.root().with(observation.span());

            var baseContext = KoraContext.empty()
                .with(io.koraframework.logging.common.MDC.MDC_KEY, new io.koraframework.logging.common.MDC())
                .with(OpentelemetryContextStorage.OPENTELEMETRY_KEY, rootOtelCtx);

            var finalContext = Observation.attach(baseContext, observation);

            KoraContext.runWith(finalContext, new JdkJobExecutionRunner(this, observation));

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
        logger.debug("JDK Job '{}#{}' stopping...", telemetry.jobClass().getCanonicalName(), telemetry.jobMethod());
        final long startedTime = TimeUtils.started();

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

            logger.info("JDK Job '{}#{}' stopped in {}", telemetry.jobClass().getCanonicalName(), telemetry.jobMethod(), TimeUtils.tookForLogging(startedTime));
        } finally {
            this.lock.unlock();
        }
    }

    private static final class JdkJobScheduleRunner implements Runnable {
        private final KoraJdkJob job;
        private final long generation;

        public JdkJobScheduleRunner(KoraJdkJob job, long generation) {
            this.job = job;
            this.generation = generation;
        }

        @Override
        public void run() {
            job.runJob(generation);
        }
    }

    private static final class JdkJobExecutionRunner implements Supplier<Void> {
        private final KoraJdkJob job;
        private final SchedulingObservation observation;

        public JdkJobExecutionRunner(KoraJdkJob job, SchedulingObservation observation) {
            this.job = job;
            this.observation = observation;
        }

        @Override
        public Void get() {
            observation.observeRun();
            try {
                job.command.run();
            } catch (Throwable e) {
                observation.observeError(e);
            } finally {
                observation.end();
            }
            return null;
        }
    }
}
