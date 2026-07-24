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
 * processor into an {@code io.koraframework.scheduling.db.job.CronJob}
 * component and registered in the database scheduler. The generated job calls
 * the annotated method on the target component when the scheduler acquires the
 * corresponding database task.
 *
 * <p>The annotated method is expected to be a no-argument job method. Job data
 * serialization is not involved in this annotation: the generated task is a
 * regular recurring scheduler job whose execution invokes the annotated method.
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
 *     <li>an object with {@code cron}, optional {@code name}, and inherited
 *     scheduling telemetry settings.</li>
 * </ul>
 *
 * <p>If both annotation and config are used, the configuration value has
 * priority for the CRON expression and the job name. A non-blank annotation
 * {@link #value()} is used as the default {@code cron} value when the configured
 * path is absent. If the configured {@code name} is missing or blank, the
 * annotation {@link #name()} value is used; if it is also blank, the generated
 * default name is {@code ClassName#methodName}.
 *
 * <p>Examples:
 * <pre>{@code
 * @ScheduleWithCron("0 * * * * *")
 * void syncEveryMinute() {}
 *
 * @ScheduleWithCron(value = "0/10 * * * * *", name = "users-sync", config = "jobs.users-sync")
 * void syncUsers() {}
 * }</pre>
 *
 * <p>Example configuration:
 * <pre>{@code
 * jobs {
 *   users-sync {
 *     cron = "0 0/5 * * * *"
 *     name = "users-sync-db"
 *   }
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface ScheduleWithCron {

    /**
     * CRON expression used to schedule the job.
     *
     * <p>This value is required when {@link #config()} is empty. When
     * {@link #config()} is set, this value becomes the default CRON expression
     * and can be overridden by configuration.
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
     * default name is {@code ClassName#methodName}. When configuration is used,
     * configured {@code name} has priority over this value.
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
