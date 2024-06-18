package ru.tinkoff.kora.scheduling.quartz;

/**
 * An annotation for scheduling a re-occurring task with CRON.
 * <p>
 * Parse the given <a href="https://www.manpagez.com/man/5/crontab/">crontab expression</a> string into a Cron Expression.
 * The string has six single space-separated time and date fields:
 * <pre>
 * {@code
 * ┌───────────── second (0-59)
 * │ ┌───────────── minute (0-59)
 * │ │ ┌───────────── hour (0-23)
 * │ │ │ ┌───────────── day of the month (1-31)
 * │ │ │ │ ┌───────────── month (1-12 or JAN-DEC)
 * │ │ │ │ │ ┌───────────── day of the week (1-7 or SUN-SAT)
 * │ │ │ │ │ │ ┌─────────────  year (empty, 1970-2099, ?)
 * │ │ │ │ │ │ │
 * │ │ │ │ │ │ │
 * * * * * * * *
 * }
 * </pre>
 * <p>
 * Special characters:
 * <ul>
 * <li> * (“all values”) - used to select all values within a field. For example, “*” in the minute field means “every minute”. </li>
 * <li> ? (“no specific value”) - useful when you need to specify something in one of the two fields in which the character is allowed, but not the other.
 * For example, if I want my trigger to fire on a particular day of the month (say, the 10th),
 * but don’t care what day of the week that happens to be, I would put “10” in the day-of-month field,
 * and “?” in the day-of-week field. See the examples below for clarification. </li>
 * </ul>
 * <p>
 * Example expressions:
 * <ul>
 * <li>{@code "0 0 * * * ?"} = the top of every hour of every day.</li>
 * <li>{@code "*\/10 * * * * ?"} = every ten seconds.</li>
 * <li>{@code "0 0 8-10 * * ?"} = 8, 9 and 10 o'clock of every day.</li>
 * <li>{@code "0 0 6,19 * * ?"} = 6:00 AM and 7:00 PM every day.</li>
 * <li>{@code "0 0/30 8-10 * * ?"} = 8:00, 8:30, 9:00, 9:30, 10:00 and 10:30 every day.</li>
 * <li>{@code "0 0 9-17 * SUN-SAT ?"} = on the hour nine-to-five weekdays</li>
 * <li>{@code "0 0 0 25 12 ?"} = every Christmas Day at midnight</li>
 * <li>{@code "0 0 0 L * ?"} = last day of the month at midnight</li>
 * <li>{@code "0 0 0 L-3 * ?"} = third-to-last day of the month at midnight</li>
 * <li>{@code "0 0 0 1W * ?"} = first weekday of the month at midnight</li>
 * <li>{@code "0 0 0 LW * ?"} = last weekday of the month at midnight</li>
 * <li>{@code "0 0 0 * * FRI"} = last Friday of the month at midnight</li>
 * <li>{@code "0 0 0 * * THUR"} = last Thursday of the month at midnight</li>
 * <li>{@code "0 0 0 ? * 5#2"} = the second Friday in the month at midnight</li>
 * <li>{@code "0 0 0 ? * MON#1"} = the first Monday in the month at midnight</li>
 * </ul>
 * <a href="https://www.quartz-scheduler.org/documentation/quartz-2.3.0/tutorials/crontrigger.html#format">Quartz Format Documentation</a>
 */
public @interface ScheduleWithCron {

    /**
     * @return The CRON expression
     */
    String value() default "";

    /**
     * @return scheduler identifier {@link org.quartz.TriggerBuilder#withIdentity(String)}
     */
    String identity() default "";

    /**
     * @return path for configuration to apply options (config > annotation options in priority)
     */
    String config() default "";
}
