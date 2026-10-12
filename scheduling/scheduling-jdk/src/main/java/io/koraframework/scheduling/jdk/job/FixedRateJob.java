package io.koraframework.scheduling.jdk.job;

import io.koraframework.scheduling.common.telemetry.SchedulingTelemetry;
import io.koraframework.scheduling.jdk.SchedulingJdkExecutor;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public final class FixedRateJob extends KoraJdkJob {

    private final Duration initialDelay;
    private final Duration period;

    public FixedRateJob(SchedulingTelemetry schedulingTelemetry, SchedulingJdkExecutor service, Runnable command, Duration initialDelay, Duration period) {
        this(schedulingTelemetry, service, command, initialDelay, period, true);
    }

    public FixedRateJob(SchedulingTelemetry schedulingTelemetry, SchedulingJdkExecutor service, Runnable command, Duration initialDelay, Duration period, boolean enabled) {
        this(schedulingTelemetry, service, command, initialDelay, period, enabled, null);
    }

    public FixedRateJob(SchedulingTelemetry schedulingTelemetry, SchedulingJdkExecutor service, Runnable command, Duration initialDelay, Duration period, boolean enabled, @Nullable ReentrantLock executionLock) {
        super(schedulingTelemetry, service, command, enabled, executionLock);
        this.initialDelay = Objects.requireNonNull(initialDelay);
        this.period = Objects.requireNonNull(period);
    }

    @Override
    protected ScheduledFuture<?> schedule(SchedulingJdkExecutor service, Runnable command) {
        var initialDelayMillis = this.initialDelay.toMillis();
        var periodMillis = this.period.toMillis();
        return service.scheduleAtFixedRate(command, initialDelayMillis, periodMillis, TimeUnit.MILLISECONDS);
    }
}
