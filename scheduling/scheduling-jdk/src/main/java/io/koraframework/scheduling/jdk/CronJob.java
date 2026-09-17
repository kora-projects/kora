package io.koraframework.scheduling.jdk;

import io.koraframework.scheduling.common.telemetry.SchedulingTelemetry;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class CronJob extends AbstractJob {

    private final CronExpression cron;
    private final Clock clock;

    public CronJob(SchedulingTelemetry telemetry, SchedulingJdkExecutor service, Runnable command, CronExpression cron) {
        this(telemetry, service, command, cron, Clock.systemDefaultZone());
    }

    CronJob(SchedulingTelemetry telemetry, SchedulingJdkExecutor service, Runnable command, CronExpression cron, Clock clock) {
        super(telemetry, service, command);
        this.cron = Objects.requireNonNull(cron);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    protected boolean rescheduleAfterRun() {
        return true;
    }

    @Override
    protected @Nullable ScheduledFuture<?> schedule(SchedulingJdkExecutor service, Runnable command) {
        var now = ZonedDateTime.now(this.clock);
        var next = this.cron.next(now);
        if (next == null) {
            return null;
        }
        var delay = Duration.between(now, next).toNanos();
        return service.scheduleOnce(command, delay, TimeUnit.NANOSECONDS);
    }
}
