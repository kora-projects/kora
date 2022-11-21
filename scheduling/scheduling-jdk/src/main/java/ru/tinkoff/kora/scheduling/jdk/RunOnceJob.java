package ru.tinkoff.kora.scheduling.jdk;

import ru.tinkoff.kora.scheduling.common.telemetry.SchedulingTelemetry;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class RunOnceJob extends AbstractJob {
    private final Duration delay;

    public RunOnceJob(SchedulingTelemetry schedulingTelemetry, ScheduledExecutorService service, Runnable command, Duration delay) {
        super(schedulingTelemetry, service, command);
        this.delay = Objects.requireNonNull(delay);
    }

    @Override
    protected ScheduledFuture<?> schedule(ScheduledExecutorService service, Runnable command) {
        var delay = this.delay.toMillis();
        return service.schedule(command, delay, TimeUnit.MILLISECONDS);
    }
}
