package ru.tinkoff.kora.scheduling.jdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.common.util.TimeUtils;
import ru.tinkoff.kora.scheduling.common.telemetry.SchedulingTelemetry;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;

public abstract class AbstractJob implements Lifecycle {

    private final Logger logger;

    private final SchedulingTelemetry telemetry;
    private final JdkSchedulingExecutor service;
    private final Runnable command;

    private final ReentrantLock lock = new ReentrantLock(true);

    private volatile boolean started = false;
    private volatile ScheduledFuture<?> scheduledFuture;

    public AbstractJob(SchedulingTelemetry telemetry, JdkSchedulingExecutor service, Runnable command) {
        this.logger = LoggerFactory.getLogger(telemetry.jobClass());
        this.telemetry = telemetry;
        this.service = service;
        this.command = command;
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

            this.scheduledFuture = this.schedule(this.service, this::runJob);

            logger.info("JDK Job '{}#{}' started in {}", telemetry.jobClass().getCanonicalName(), telemetry.jobMethod(),
                TimeUtils.tookForLogging(started));
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
            MDC.clear();
            Context.clear();
            var ctx = Context.current();
            var telemetryCtx = this.telemetry.get(ctx);
            try {
                this.command.run();
                telemetryCtx.close(null);
            } catch (Throwable e) {
                telemetryCtx.close(e);
            }
        } finally {
            this.lock.unlock();
        }
    }

    protected abstract ScheduledFuture<?> schedule(JdkSchedulingExecutor service, Runnable command);

    @Override
    public final void release() {
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
            f.cancel(false);

            logger.info("JDK Job '{}#{}' stopped in {}", telemetry.jobClass().getCanonicalName(), telemetry.jobMethod(), TimeUtils.tookForLogging(started));
        } finally {
            this.lock.unlock();
        }
    }
}
