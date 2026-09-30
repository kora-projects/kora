package io.koraframework.scheduling.quartz.util;

import org.jspecify.annotations.Nullable;
import org.quartz.CronExpression;
import org.quartz.CronScheduleBuilder;

import java.text.ParseException;
import java.time.ZoneId;
import java.util.TimeZone;

/**
 * Builds CRON schedules for the triggers of {@link io.koraframework.scheduling.quartz.annotation.ScheduleQuartzWithCron} jobs.
 */
public final class QuartzCronUtils {

    private static final String FORMAT = """
        Quartz expects 6 or 7 fields, the year field is optional, and exactly one of day-of-month and day-of-week must be '?':
        ┌───────────── second (0-59)
        │ ┌───────────── minute (0-59)
        │ │ ┌───────────── hour (0-23)
        │ │ │ ┌───────────── day of the month (1-31, L, W or ?)
        │ │ │ │ ┌───────────── month (1-12 or JAN-DEC)
        │ │ │ │ │ ┌───────────── day of the week (1-7 or SUN-SAT, 1 is Sunday, L, # or ?)
        │ │ │ │ │ │ ┌───────────── year (optional)
        │ │ │ │ │ │ │
        * * * ? * * *
        Examples:
          '0 0 15 * * ?' runs every day at 15:00
          '0 0 9-17 ? * MON-FRI' runs every hour from 9:00 through 17:00 on weekdays
        See the Javadoc of @ScheduleQuartzWithCron for details.""";

    private QuartzCronUtils() {}

    /**
     * @param cron      CRON expression, validated with an error message that describes the expected format
     * @param zoneId    time zone the expression is evaluated in, the JVM default time zone when {@code null}
     * @param jobClass  class declaring the job method, used in the error message
     * @param jobMethod job method, used in the error message
     * @throws IllegalArgumentException when the expression is invalid
     */
    public static CronScheduleBuilder cronSchedule(String cron, @Nullable ZoneId zoneId, Class<?> jobClass, String jobMethod) {
        CronExpression expression;
        try {
            expression = new CronExpression(cron);
        } catch (ParseException e) {
            throw new IllegalArgumentException("Invalid CRON expression '%s' for Quartz job '%s#%s': %s\n%s".formatted(
                cron, jobClass.getCanonicalName(), jobMethod, e.getMessage(), FORMAT), e);
        }
        if (zoneId != null) {
            expression.setTimeZone(TimeZone.getTimeZone(zoneId));
        }
        return CronScheduleBuilder.cronSchedule(expression);
    }
}
