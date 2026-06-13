package io.koraframework.scheduling.jdk;

import io.koraframework.application.graph.Lifecycle;
import io.koraframework.common.telemetry.Observation;
import io.koraframework.common.telemetry.OpentelemetryContext;
import io.koraframework.common.util.TimeUtils;
import io.koraframework.scheduling.common.telemetry.SchedulingTelemetry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public final class CronJob implements Lifecycle {

    private final Logger logger;
    private final ReentrantLock lock = new ReentrantLock(true);
    private final SchedulingTelemetry telemetry;
    private final SchedulingJdkExecutor service;
    private final Runnable command;
    private final CronExpression cron;

    private volatile boolean started = false;
    private volatile ScheduledFuture<?> scheduledFuture;

    public CronJob(SchedulingTelemetry telemetry, SchedulingJdkExecutor service, Runnable command, CronExpression cron) {
        this.logger = LoggerFactory.getLogger(telemetry.jobClass());
        this.telemetry = telemetry;
        this.service = service;
        this.command = command;
        this.cron = Objects.requireNonNull(cron);
    }

    @Override
    public void init() {
        this.lock.lock();
        try {
            if (this.started) {
                return;
            }
            this.started = true;
            this.logger.debug("JDK Job '{}#{}' starting...", telemetry.jobClass().getCanonicalName(), telemetry.jobMethod());
            final long started = TimeUtils.started();

            this.scheduleNext();

            this.logger.info("JDK Job '{}#{}' started in {}", telemetry.jobClass().getCanonicalName(), telemetry.jobMethod(),
                TimeUtils.tookForLogging(started));
        } finally {
            this.lock.unlock();
        }
    }

    @Override
    public void release() {
        this.logger.debug("JDK Job '{}#{}' stopping...", telemetry.jobClass().getCanonicalName(), telemetry.jobMethod());
        final long started = TimeUtils.started();

        this.lock.lock();
        try {
            if (!this.started) {
                return;
            }
            this.started = false;
            var future = this.scheduledFuture;
            this.scheduledFuture = null;
            if (future != null) {
                future.cancel(false);
            }
            this.logger.info("JDK Job '{}#{}' stopped in {}", telemetry.jobClass().getCanonicalName(), telemetry.jobMethod(), TimeUtils.tookForLogging(started));
        } finally {
            this.lock.unlock();
        }
    }

    private void runJob() {
        this.lock.lock();
        try {
            if (!this.started) {
                return;
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
            this.scheduleNext();
        } finally {
            this.lock.unlock();
        }
    }

    private void scheduleNext() {
        var now = ZonedDateTime.now();
        var next = this.cron.next(now);
        if (next == null) {
            this.logger.warn("JDK Job '{}#{}' won't be scheduled because cron expression has no next fire time: {}",
                this.telemetry.jobClass().getCanonicalName(), this.telemetry.jobMethod(), this.cron);
            this.started = false;
            this.scheduledFuture = null;
            return;
        }
        var delay = Math.max(0, Duration.between(now, next).toMillis());
        this.scheduledFuture = this.service.scheduleOnce(this::runJob, delay, TimeUnit.MILLISECONDS);
    }
}
