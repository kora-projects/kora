package ru.tinkoff.kora.scheduling.quartz;

/**
 * An annotation for scheduling a re-occurring task with CRON.
 * <p>
 * Parse the given <a href="https://www.manpagez.com/man/5/crontab/">crontab expression</a> string into a Cron Expression.
 * The string has six single space-separated time and date fields:
 * ` ┌───────────── second (0-59)
 * ` │ ┌───────────── minute (0 - 59)
 * ` │ │ ┌───────────── hour (0 - 23)
 * ` │ │ │ ┌───────────── day of the month (1 - 31)
 * ` │ │ │ │ ┌───────────── month (1 - 12) (or JAN-DEC)
 * ` │ │ │ │ │ ┌───────────── day of the week (0 - 7)
 * ` │ │ │ │ │ │          (0 or 7 is Sunday, or MON-SUN)
 * ` │ │ │ │ │ │
 * ` * * * * * *
 *
 * <p>Example expressions:
 * <ul>
 * <li>{@code "0 0 * * * *"} = the top of every hour of every day.</li>
 * <li>{@code "*\/10 * * * * *"} = every ten seconds.</li>
 * <li>{@code "0 0 8-10 * * *"} = 8, 9 and 10 o'clock of every day.</li>
 * <li>{@code "0 0 6,19 * * *"} = 6:00 AM and 7:00 PM every day.</li>
 * <li>{@code "0 0/30 8-10 * * *"} = 8:00, 8:30, 9:00, 9:30, 10:00 and 10:30 every day.</li>
 * <li>{@code "0 0 9-17 * * MON-FRI"} = on the hour nine-to-five weekdays</li>
 * <li>{@code "0 0 0 25 12 ?"} = every Christmas Day at midnight</li>
 * <li>{@code "0 0 0 L * *"} = last day of the month at midnight</li>
 * <li>{@code "0 0 0 L-3 * *"} = third-to-last day of the month at midnight</li>
 * <li>{@code "0 0 0 1W * *"} = first weekday of the month at midnight</li>
 * <li>{@code "0 0 0 LW * *"} = last weekday of the month at midnight</li>
 * <li>{@code "0 0 0 * * 5L"} = last Friday of the month at midnight</li>
 * <li>{@code "0 0 0 * * THUL"} = last Thursday of the month at midnight</li>
 * <li>{@code "0 0 0 ? * 5#2"} = the second Friday in the month at midnight</li>
 * <li>{@code "0 0 0 ? * MON#1"} = the first Monday in the month at midnight</li>
 * </ul>
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
