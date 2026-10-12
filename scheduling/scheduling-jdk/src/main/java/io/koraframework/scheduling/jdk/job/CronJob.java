package io.koraframework.scheduling.jdk.job;

import io.koraframework.scheduling.common.telemetry.SchedulingTelemetry;
import io.koraframework.scheduling.jdk.SchedulingJdkExecutor;
import io.koraframework.scheduling.jdk.util.CronExpression;
import org.jspecify.annotations.Nullable;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A JDK scheduled job triggered by a CRON expression, see
 * {@link io.koraframework.scheduling.jdk.annotation.ScheduleJdkWithCron} for the expression format.
 */
public final class CronJob extends KoraJdkJob {

    private static final String FORMAT = """
        The JDK scheduler expects 5, 6 or 7 fields, the second and year fields are optional:
        ┌───────────── second (0-59, optional)
        │ ┌───────────── minute (0-59)
        │ │ ┌───────────── hour (0-23)
        │ │ │ ┌───────────── day of the month (1-31 or ?)
        │ │ │ │ ┌───────────── month (1-12 or JAN-DEC)
        │ │ │ │ │ ┌───────────── day of the week (1-7 or SUN-SAT, 0 and 1 are Sunday, or ?)
        │ │ │ │ │ │ ┌───────────── year (1970-2099 or ?, optional)
        │ │ │ │ │ │ │
        * * * * * * *
        Modifiers L, W, # and C are not supported.
        Examples:
          '0 0 15 * * ?' runs every day at 15:00
          '0 0 9-17 * * MON-FRI' runs every hour from 9:00 through 17:00 on weekdays
        See the Javadoc of @ScheduleJdkWithCron for details.""";

    private final CronExpression cron;
    private final Clock clock;

    public CronJob(SchedulingTelemetry telemetry, SchedulingJdkExecutor service, Runnable command, CronExpression cron) {
        this(telemetry, service, command, cron, Clock.systemDefaultZone(), true, null);
    }

    /**
     * @param cron    CRON expression, validated with an error message that describes the expected format
     * @param zoneId  time zone the expression is evaluated in, the JVM default time zone when {@code null}
     * @param enabled {@code false} when the job is disabled by the {@code enabled} key of its configuration
     * @throws IllegalArgumentException when the expression is invalid
     */
    public CronJob(SchedulingTelemetry telemetry, SchedulingJdkExecutor service, Runnable command, String cron, @Nullable ZoneId zoneId, boolean enabled) {
        this(telemetry, service, command, cron, zoneId, enabled, null);
    }

    /**
     * @param executionLock see {@link KoraJdkJob#KoraJdkJob(SchedulingTelemetry, SchedulingJdkExecutor, Runnable, boolean, ReentrantLock)}
     */
    public CronJob(SchedulingTelemetry telemetry, SchedulingJdkExecutor service, Runnable command, String cron, @Nullable ZoneId zoneId, boolean enabled, @Nullable ReentrantLock executionLock) {
        this(telemetry, service, command, parse(telemetry, cron), zoneId == null ? Clock.systemDefaultZone() : Clock.system(zoneId), enabled, executionLock);
    }

    CronJob(SchedulingTelemetry telemetry, SchedulingJdkExecutor service, Runnable command, CronExpression cron, Clock clock) {
        this(telemetry, service, command, cron, clock, true, null);
    }

    private CronJob(SchedulingTelemetry telemetry, SchedulingJdkExecutor service, Runnable command, CronExpression cron, Clock clock, boolean enabled, @Nullable ReentrantLock executionLock) {
        super(telemetry, service, command, enabled, executionLock);
        this.cron = Objects.requireNonNull(cron);
        this.clock = Objects.requireNonNull(clock);
    }

    private static CronExpression parse(SchedulingTelemetry telemetry, String cron) {
        try {
            return CronExpression.parse(cron);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid CRON expression '%s' for JDK job '%s#%s': %s\n%s".formatted(
                cron, telemetry.jobClass().getCanonicalName(), telemetry.jobMethod(), e.getMessage(), FORMAT), e);
        }
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
