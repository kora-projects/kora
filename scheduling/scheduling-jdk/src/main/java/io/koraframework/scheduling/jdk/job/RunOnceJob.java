package io.koraframework.scheduling.jdk.job;

import io.koraframework.scheduling.common.telemetry.SchedulingTelemetry;
import io.koraframework.scheduling.jdk.SchedulingJdkExecutor;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public final class RunOnceJob extends KoraJdkJob {

    private final Duration delay;

    public RunOnceJob(SchedulingTelemetry schedulingTelemetry, SchedulingJdkExecutor service, Runnable command, Duration delay) {
        this(schedulingTelemetry, service, command, delay, true);
    }

    public RunOnceJob(SchedulingTelemetry schedulingTelemetry, SchedulingJdkExecutor service, Runnable command, Duration delay, boolean enabled) {
        this(schedulingTelemetry, service, command, delay, enabled, null);
    }

    public RunOnceJob(SchedulingTelemetry schedulingTelemetry, SchedulingJdkExecutor service, Runnable command, Duration delay, boolean enabled, @Nullable ReentrantLock executionLock) {
        super(schedulingTelemetry, service, command, enabled, executionLock);
        this.delay = Objects.requireNonNull(delay);
    }

    @Override
    protected ScheduledFuture<?> schedule(SchedulingJdkExecutor service, Runnable command) {
        var delay = this.delay.toMillis();
        return service.scheduleOnce(command, delay, TimeUnit.MILLISECONDS);
    }
}
