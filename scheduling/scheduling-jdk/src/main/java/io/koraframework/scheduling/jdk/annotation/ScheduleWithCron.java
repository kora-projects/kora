package io.koraframework.scheduling.jdk.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation for scheduling a re-occurring task with CRON.
 * <p>
 * The expression is parsed and evaluated by {@link io.koraframework.scheduling.jdk.CronExpression}.
 * It may contain five, six or seven single space-separated fields:
 * <pre>
 * {@code
 * 5 fields: minute hour day-of-month month day-of-week
 * 6 fields: second minute hour day-of-month month day-of-week
 * 7 fields: second minute hour day-of-month month day-of-week year
 *
 * ┌───────────── second (0-59)
 * │ ┌───────────── minute (0-59)
 * │ │ ┌───────────── hour (0-23)
 * │ │ │ ┌───────────── day of the month (1-31)
 * │ │ │ │ ┌───────────── month (1-12 or JAN-DEC)
 * │ │ │ │ │ ┌───────────── day of the week (1-7 or SUN-SAT)
 * │ │ │ │ │ │ ┌───────────── year (empty, 1970-2099, ?)
 * │ │ │ │ │ │ │
 * │ │ │ │ │ │ │
 * * * * * * * *
 * }
 * </pre>
 * <p>
 * Five-field expressions omit the seconds field and are evaluated with {@code 0} seconds.
 * The year field is optional; when it is absent, all years from 1970 to 2099 are allowed.
 * Day-of-week also accepts {@code 0} as Sunday.
 * <p>
 * Special characters:
 * <ul>
 * <li>{@code *} selects all values in the field, for example {@code *} in the minute field means every minute.</li>
 * <li>{@code ?} means no specific value and is supported only in day-of-month, day-of-week and year fields.</li>
 * <li>{@code ,} separates explicit values, for example {@code 6,18} in the hour field.</li>
 * <li>{@code -} selects an inclusive range, for example {@code MON-FRI} or {@code 9-17}.</li>
 * <li>{@code /} selects values by step, for example {@code *&#47;10} in the seconds field or {@code 5/10}.</li>
 * </ul>
 * <p>
 * Quartz-specific modifiers {@code L}, {@code W}, {@code #} and {@code C} are not supported by the JDK scheduler.
 * <p>
 * Example expressions:
 * <ul>
 * <li>{@code "0 * * * * *"} = the top of every minute.</li>
 * <li>{@code "*&#47;10 * * * * *"} = every ten seconds.</li>
 * <li>{@code "0 0 * * * ?"} = the top of every hour.</li>
 * <li>{@code "0 0 6,19 * * ?"} = 6:00 and 19:00 every day.</li>
 * <li>{@code "0 0/30 8-10 * * ?"} = every 30 minutes from 8:00 through 10:30 every day.</li>
 * <li>{@code "0 0 9-17 ? * MON-FRI"} = every hour from 9:00 through 17:00 on weekdays.</li>
 * <li>{@code "0 0 0 25 DEC ?"} = every Christmas Day at midnight.</li>
 * <li>{@code "0 0 0 29 FEB ?"} = every leap day at midnight.</li>
 * <li>{@code "0 0 0 1 JAN ? 2027"} = January 1, 2027 at midnight.</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface ScheduleWithCron {

    /**
     * @return The CRON expression
     */
    String value() default "";

    /**
     * @return path for configuration to apply options (config > annotation options in priority)
     */
    String config() default "";
}
