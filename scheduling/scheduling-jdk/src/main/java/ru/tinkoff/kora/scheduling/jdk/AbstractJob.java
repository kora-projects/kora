package ru.tinkoff.kora.scheduling.jdk;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.scheduling.common.telemetry.SchedulingTelemetry;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractJob implements Lifecycle {

    private final Logger logger;
    private final SchedulingTelemetry telemetry;
    private final JdkSchedulingExecutor service;
    private final Runnable command;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private volatile ScheduledFuture<?> scheduledFuture;

    public AbstractJob(SchedulingTelemetry telemetry, JdkSchedulingExecutor service, Runnable command) {
        this.telemetry = telemetry;
        this.service = service;
        this.command = command;
        this.logger = LoggerFactory.getLogger(telemetry.jobClass());
    }

    @Override
    public final Mono<?> init() {
        return Mono.fromRunnable(() -> {
            if (this.started.compareAndSet(false, true)) {
                logger.debug("Scheduled Job '{}#{}' starting...", telemetry.jobClass().getCanonicalName(), telemetry.jobMethod());
                final long started = System.nanoTime();

                this.scheduledFuture = this.schedule(this.service, this::runJob);

                logger.info("Started Scheduled Job '{}#{}' started in {}", telemetry.jobClass().getCanonicalName(), telemetry.jobMethod(),
                    Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
            }
        });
    }

    private void runJob() {
        MDC.clear();
        Context.clear();
        var ctx = Context.current();
        var telemetryCtx = this.telemetry.get(ctx);
        try {
            this.command.run();
            telemetryCtx.close(null);
        } catch (Exception e) {
            logger.warn("Uncaught exception while running job: {}#{}", this.telemetry.jobClass().getCanonicalName(), this.telemetry.jobMethod(), e);
            telemetryCtx.close(e);
        }
    }

    protected abstract ScheduledFuture<?> schedule(JdkSchedulingExecutor service, Runnable command);

    @Override
    public final Mono<?> release() {
        return Mono.fromRunnable(() -> {
            if (this.started.compareAndSet(true, false)) {
                logger.debug("Scheduled Job '{}#{}' stopping...", telemetry.jobClass().getCanonicalName(), telemetry.jobMethod());
                final long started = System.nanoTime();

                var f = this.scheduledFuture;
                this.scheduledFuture = null;
                f.cancel(true);

                logger.info("Scheduled Job '{}#{}' stopped in {}", telemetry.jobClass().getCanonicalName(), telemetry.jobMethod(),
                    Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
            }
        });
    }
}
