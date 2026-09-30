package io.koraframework.scheduling.db.scheduler.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a component method as a persistent database scheduled job that is
 * triggered by a CRON expression.
 *
 * <p>The annotated method is converted by the Kora scheduling annotation
 * processor into an {@code io.koraframework.scheduling.db.scheduler.job.CronJob}
 * component and registered in the database scheduler. The generated job calls
 * the annotated method on the target component when the scheduler acquires the
 * corresponding database task.
 *
 * <p>The annotated method is expected to be a no-argument job method. Job data
 * serialization is not involved in this annotation: the generated task is a
 * regular recurring scheduler job whose execution invokes the annotated method.
 *
 * <p>The CRON expression is parsed by db-scheduler with the Spring 5.3 dialect
 * of cron-utils and evaluated in the time zone described below. It must contain
 * exactly six single space-separated fields:
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
 * <p>Special characters:
 * <ul>
 * <li>{@code *} selects all values in the field, for example {@code *} in the minute field means every minute.</li>
 * <li>{@code ?} means no specific value and is supported in day-of-month and day-of-week fields.</li>
 * <li>{@code ,} separates explicit values, for example {@code 6,18} in the hour field.</li>
 * <li>{@code -} selects an inclusive range, for example {@code MON-FRI} or {@code 9-17}.</li>
 * <li>{@code /} selects values by step, for example {@code *&#47;10} in the seconds field or {@code 5/10}.</li>
 * <li>{@code L} selects the last day of the month ({@code L}, {@code L-3}) or the last given
 * day of the week in the month ({@code 5L} is the last Friday).</li>
 * <li>{@code W} selects the weekday nearest to the given day of the month ({@code 1W}, {@code LW}).</li>
 * <li>{@code #} selects the n-th day of the week in the month ({@code MON#2} is the second Monday).</li>
 * </ul>
 *
 * <p>Macros {@code @yearly}, {@code @monthly}, {@code @weekly}, {@code @daily} and
 * {@code @hourly} are accepted. The single value {@code -} disables the job: an
 * already scheduled execution is removed from the table on startup, which is
 * useful for switching a job off through configuration.
 *
 * <p>Example expressions:
 * <ul>
 * <li>{@code "0 * * * * *"} = the top of every minute.</li>
 * <li>{@code "*&#47;10 * * * * *"} = every ten seconds.</li>
 * <li>{@code "0 0 * * * *"} = the top of every hour.</li>
 * <li>{@code "0 0 6,19 * * *"} = 6:00 and 19:00 every day.</li>
 * <li>{@code "0 0/30 8-10 * * *"} = every 30 minutes from 8:00 through 10:30 every day.</li>
 * <li>{@code "0 0 9-17 * * MON-FRI"} = every hour from 9:00 through 17:00 on weekdays.</li>
 * <li>{@code "0 0 0 25 DEC ?"} = every Christmas Day at midnight.</li>
 * <li>{@code "0 0 0 L * *"} = the last day of every month at midnight.</li>
 * <li>{@code "0 0 0 * * MON#2"} = the second Monday of every month at midnight.</li>
 * <li>{@code "@daily"} = every day at midnight.</li>
 * </ul>
 *
 * <p>Configuration can be provided in two ways:
 * <ul>
 *     <li>directly in the annotation through {@link #value()}, {@link #name()},
 *     and {@link #config()};</li>
 *     <li>through the Kora configuration path specified by {@link #config()}.</li>
 * </ul>
 *
 * <p>When {@link #config()} is empty, {@link #value()} must contain a non-blank
 * CRON expression. When {@link #config()} is set, the processor generates a
 * config mapper for the selected path. The config may be either:
 * <ul>
 *     <li>a string value containing the CRON expression; or</li>
 *     <li>an object with {@code cron} and inherited scheduling telemetry
 *     settings.</li>
 * </ul>
 *
 * <p>If both annotation and config are used, the configuration value has
 * priority for the CRON expression. A non-blank annotation {@link #value()} is
 * used as the default {@code cron} value when the configured path is absent.
 * The job name is taken from {@link #name()} only; if it is blank, the generated
 * default name is {@code CanonicalClassName#methodName}.
 *
 * <p>Examples:
 * <pre>{@code
 * @ScheduleDbWithCron("0 * * * * *")
 * void syncEveryMinute() {}
 *
 * @ScheduleDbWithCron(value = "0/10 * * * * *", name = "users-sync", config = "jobs.users-sync")
 * void syncUsers() {}
 * }</pre>
 *
 * <p>Example configuration:
 * <pre>{@code
 * jobs {
 *   users-sync {
 *     cron = "0 0/5 * * * *"
 *   }
 * }
 * }</pre>
 *
 * <p>The CRON expression is evaluated in the time zone of the {@link java.time.ZoneId} component tagged with
 * {@code io.koraframework.scheduling.common.SchedulingModule}, or in the JVM default time zone when there is
 * no such component:
 * <pre>{@code
 * @Tag(SchedulingModule.class)
 * default ZoneId schedulingZone() {
 *     return ZoneId.of("Europe/Moscow");
 * }
 * }</pre>
 *
 * <p>A job declared with {@code config} can be disabled by the {@code enabled} key of its configuration,
 * for example {@code jobs.my-job.enabled = false}, see
 * {@link io.koraframework.scheduling.common.SchedulingJobConfig#enabled()}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface ScheduleDbWithCron {

    /**
     * CRON expression used to schedule the job.
     *
     * <p>This value is required when {@link #config()} is empty. When
     * {@link #config()} is set, this value becomes the default CRON expression
     * and can be overridden by configuration. See the class documentation for
     * the expression format.
     *
     * @return CRON expression or empty string when it is supplied only through
     * configuration
     */
    String value() default "";

    /**
     * Logical name of the generated database scheduled job.
     *
     * <p>The name is used as the scheduler task name. It must be stable because
     * database scheduler state is associated with task names. If omitted, the
     * default name is {@code CanonicalClassName#methodName}, for example
     * {@code com.example.UserJobs#sync}. Moving or renaming the class or method
     * changes the default name, so set an explicit name for jobs whose state
     * must survive refactoring.
     *
     * <p>The name is taken from the annotation only and cannot be overridden by
     * configuration. It must not be longer than 350 characters, the size of the
     * {@code task_name} column in the bundled schema.
     *
     * @return explicit job name or empty string for the generated default name
     */
    String name() default "";

    /**
     * Kora configuration path for this job.
     *
     * <p>When set, the processor generates a config interface for this path.
     * The path may point either to a string CRON value or to an object with a
     * {@code cron} field and optional scheduling settings. Config values have
     * priority over annotation values.
     *
     * @return config path or empty string to use annotation-only configuration
     */
    String config() default "";
}
