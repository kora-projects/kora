package ru.tinkoff.kora.scheduling.jdk;

import ru.tinkoff.kora.scheduling.common.telemetry.SchedulingTelemetry;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class FixedDelayJob extends AbstractJob {
    private final Duration initialDelay;
    private final Duration delay;

    public FixedDelayJob(SchedulingTelemetry schedulingTelemetry, ScheduledExecutorService service, Runnable command, Duration initialDelay, Duration delay) {
        super(schedulingTelemetry, service, command);
        this.initialDelay = Objects.requireNonNull(initialDelay);
        this.delay = Objects.requireNonNull(delay);
    }

    @Override
    protected ScheduledFuture<?> schedule(ScheduledExecutorService service, Runnable command) {
        var initialDelay = this.initialDelay.toMillis();
        var delay = this.delay.toMillis();
        return service.scheduleWithFixedDelay(command, initialDelay, delay, TimeUnit.MILLISECONDS);
    }
}
