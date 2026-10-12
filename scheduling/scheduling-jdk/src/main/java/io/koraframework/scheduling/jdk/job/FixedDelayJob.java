package io.koraframework.scheduling.jdk.job;

import io.koraframework.scheduling.common.telemetry.SchedulingTelemetry;
import io.koraframework.scheduling.jdk.SchedulingJdkExecutor;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public final class FixedDelayJob extends KoraJdkJob {

    private final Duration initialDelay;
    private final Duration delay;

    public FixedDelayJob(SchedulingTelemetry schedulingTelemetry, SchedulingJdkExecutor service, Runnable command, Duration initialDelay, Duration delay) {
        this(schedulingTelemetry, service, command, initialDelay, delay, true);
    }

    public FixedDelayJob(SchedulingTelemetry schedulingTelemetry, SchedulingJdkExecutor service, Runnable command, Duration initialDelay, Duration delay, boolean enabled) {
        this(schedulingTelemetry, service, command, initialDelay, delay, enabled, null);
    }

    public FixedDelayJob(SchedulingTelemetry schedulingTelemetry, SchedulingJdkExecutor service, Runnable command, Duration initialDelay, Duration delay, boolean enabled, @Nullable ReentrantLock executionLock) {
        super(schedulingTelemetry, service, command, enabled, executionLock);
        this.initialDelay = Objects.requireNonNull(initialDelay);
        this.delay = Objects.requireNonNull(delay);
    }

    @Override
    protected ScheduledFuture<?> schedule(SchedulingJdkExecutor service, Runnable command) {
        var initialDelay = this.initialDelay.toMillis();
        var delay = this.delay.toMillis();
        return service.scheduleWithFixedDelay(command, initialDelay, delay, TimeUnit.MILLISECONDS);
    }
}
