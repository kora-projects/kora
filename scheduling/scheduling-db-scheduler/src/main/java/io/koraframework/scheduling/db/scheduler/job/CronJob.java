package io.koraframework.scheduling.db.scheduler.job;

import com.github.kagkarlsson.scheduler.task.helper.RecurringTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import com.github.kagkarlsson.scheduler.task.schedule.CronSchedule;
import io.koraframework.scheduling.common.telemetry.SchedulingTelemetry;
import org.jspecify.annotations.Nullable;

import java.time.ZoneId;

/**
 * A database scheduled job triggered by a CRON expression.
 *
 * <p>The job is registered in db-scheduler as a recurring task named after the
 * job name. Its state is stored in the scheduler table, so a changed CRON
 * expression is applied on the next application start.
 *
 * <p>The CRON expression is parsed by db-scheduler with the Spring 5.3 dialect
 * of cron-utils and evaluated in the configured time zone, or in the JVM default
 * time zone when none is configured. It must contain exactly six single
 * space-separated fields:
 * <pre>
 * {@code
 * ┌───────────── second (0-59)
 * │ ┌───────────── minute (0-59)
 * │ │ ┌───────────── hour (0-23)
 * │ │ │ ┌───────────── day of the month (1-31)
 * │ │ │ │ ┌───────────── month (1-12 or JAN-DEC)
 * │ │ │ │ │ ┌───────────── day of the week (0-7 or MON-SUN, 0 and 7 are Sunday)
 * │ │ │ │ │ │
 * │ │ │ │ │ │
 * * * * * * *
 * }
 * </pre>
 *
 * <p>Five-field expressions without seconds and seven-field expressions with a
 * year are rejected. Unlike the JDK and Quartz schedulers, numeric days of the
 * week start from Monday: {@code 1} is Monday, {@code 5} is Friday, and both
 * {@code 0} and {@code 7} are Sunday. Prefer day names such as {@code MON-FRI}
 * to keep expressions portable between schedulers.
 *
 * <p>The special characters {@code * ? , - /}, the modifiers {@code L}, {@code W}
 * and {@code #}, and the macros {@code @yearly}, {@code @monthly},
 * {@code @weekly}, {@code @daily} and {@code @hourly} are supported. The single
 * value {@code -} disables the job and removes its scheduled execution on
 * startup, the same as a job disabled by the {@code enabled} key of its
 * configuration. See
 * {@link io.koraframework.scheduling.db.scheduler.annotation.ScheduleDbWithCron}
 * for a description of every special character and example expressions.
 */
public final class CronJob extends KoraDbJob {

    private static final String DISABLED = "-";
    private static final String FORMAT = """
        The database scheduler expects exactly 6 fields, a macro such as @daily, or '-' to disable the job:
        ┌───────────── second (0-59)
        │ ┌───────────── minute (0-59)
        │ │ ┌───────────── hour (0-23)
        │ │ │ ┌───────────── day of the month (1-31, L, W or ?)
        │ │ │ │ ┌───────────── month (1-12 or JAN-DEC)
        │ │ │ │ │ ┌───────────── day of the week (0-7 or MON-SUN, 0 and 7 are Sunday, L, # or ?)
        │ │ │ │ │ │
        * * * * * *
        Examples:
          '0 0 15 * * *' runs every day at 15:00
          '0 0 9-17 * * MON-FRI' runs every hour from 9:00 through 17:00 on weekdays
        See the Javadoc of @ScheduleDbWithCron for details.""";

    private final RecurringTask<Void> task;

    public CronJob(SchedulingTelemetry telemetry, Runnable command, String name, String cron) {
        this(telemetry, command, name, cron, null, true);
    }

    /**
     * @param zoneId  time zone the expression is evaluated in, the JVM default time zone when {@code null}
     * @param enabled {@code false} when the job is disabled by the {@code enabled} key of its configuration,
     *                its scheduled execution is then removed on startup
     * @throws IllegalArgumentException when the expression is invalid
     */
    public CronJob(SchedulingTelemetry telemetry, Runnable command, String name, String cron, @Nullable ZoneId zoneId, boolean enabled) {
        super(telemetry, command, name);
        var zone = zoneId == null ? ZoneId.systemDefault() : zoneId;
        CronSchedule schedule;
        try {
            schedule = new CronSchedule(enabled ? cron : DISABLED, zone);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid CRON expression '%s' for database scheduled job %s: %s\n%s".formatted(cron, this, e.getMessage(), FORMAT), e);
        }
        this.task = Tasks.recurring(name, schedule).execute((instance, context) -> this.runJob());
    }

    @Override
    public RecurringTask<Void> task() {
        return this.task;
    }
}
