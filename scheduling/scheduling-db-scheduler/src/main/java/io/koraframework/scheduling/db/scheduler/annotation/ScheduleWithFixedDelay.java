package io.koraframework.scheduling.db.scheduler.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.temporal.ChronoUnit;

/**
 * Marks a component method as a persistent database scheduled job that is
 * triggered repeatedly with a fixed delay between executions.
 *
 * <p>The annotated method is converted by the Kora scheduling annotation
 * processor into an {@code io.koraframework.scheduling.db.job.FixedDelayJob}
 * component and registered in the database scheduler. The generated job calls
 * the annotated method on the target component when the scheduler acquires the
 * corresponding database task.
 *
 * <p>The annotated method is expected to be a no-argument job method. Job data
 * serialization is not involved in this annotation: the generated task is a
 * regular recurring scheduler job whose execution invokes the annotated method.
 *
 * <p>When {@link #config()} is empty, {@link #delay()} must be greater than
 * zero. {@link #initialDelay()} may be zero, which means that the first
 * execution is eligible immediately. Both values are interpreted in
 * {@link #unit()} and converted to {@code java.time.Duration} in generated
 * code.
 *
 * <p>When {@link #config()} is set, the processor generates a config mapper for
 * the selected path. The generated config object contains:
 * <ul>
 *     <li>{@code delay} - required unless {@link #delay()} is provided as an
 *     annotation default;</li>
 *     <li>{@code initialDelay} - optional and defaults to the annotation value;
 *     </li>
 *     <li>{@code name} - optional job name override;</li>
 *     <li>inherited scheduling telemetry settings.</li>
 * </ul>
 *
 * <p>If both annotation and config are used, configured values have priority.
 * If the configured {@code name} is missing or blank, the annotation
 * {@link #name()} value is used; if it is also blank, the generated default
 * name is {@code ClassName#methodName}.
 *
 * <p>Examples:
 * <pre>{@code
 * @ScheduleWithFixedDelay(delay = 1, unit = ChronoUnit.MINUTES)
 * void refreshCache() {}
 *
 * @ScheduleWithFixedDelay(initialDelay = 5, delay = 30, unit = ChronoUnit.SECONDS,
 *                         name = "cache-refresh", config = "jobs.cache-refresh")
 * void refreshCacheFromDb() {}
 * }</pre>
 *
 * <p>Example configuration:
 * <pre>{@code
 * jobs {
 *   cache-refresh {
 *     delay = "60s"
 *     initialDelay = "10s"
 *     name = "cache-refresh-db"
 *   }
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface ScheduleWithFixedDelay {

    /**
     * Delay before the first execution.
     *
     * <p>The value is interpreted in {@link #unit()}. Zero means no additional
     * startup delay. When {@link #config()} is set, this value is used as the
     * default {@code initialDelay} in generated configuration.
     *
     * @return initial delay value in {@link #unit()}
     */
    long initialDelay() default 0;

    /**
     * Delay between completed execution and the next execution.
     *
     * <p>The value is interpreted in {@link #unit()}. This value must be
     * greater than zero when {@link #config()} is empty. When {@link #config()}
     * is set, this value becomes a default and can be overridden by
     * configuration.
     *
     * @return fixed delay value in {@link #unit()}
     */
    long delay() default 0;

    /**
     * Time unit used for {@link #initialDelay()} and {@link #delay()}.
     *
     * @return chrono unit used to convert numeric annotation values to
     * {@code java.time.Duration}
     */
    ChronoUnit unit() default ChronoUnit.MILLIS;

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
     * Config values have priority over annotation values.
     *
     * @return config path or empty string to use annotation-only configuration
     */
    String config() default "";
}
