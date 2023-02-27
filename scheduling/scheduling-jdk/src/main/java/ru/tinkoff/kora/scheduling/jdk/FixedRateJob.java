package ru.tinkoff.kora.scheduling.jdk;

import ru.tinkoff.kora.scheduling.common.telemetry.SchedulingTelemetry;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class FixedRateJob extends AbstractJob {
    private final Duration initialDelay;
    private final Duration period;

    public FixedRateJob(SchedulingTelemetry schedulingTelemetry, JdkSchedulingExecutor service, Runnable command, Duration initialDelay, Duration period) {
        super(schedulingTelemetry, service, command);
        this.initialDelay = Objects.requireNonNull(initialDelay);
        this.period = Objects.requireNonNull(period);
    }

    @Override
    protected ScheduledFuture<?> schedule(JdkSchedulingExecutor service, Runnable command) {
        var initialDelayMillis = this.initialDelay.toMillis();
        var periodMillis = this.period.toMillis();
        return service.scheduleAtFixedRate(command, initialDelayMillis, periodMillis, TimeUnit.MILLISECONDS);
    }
}
