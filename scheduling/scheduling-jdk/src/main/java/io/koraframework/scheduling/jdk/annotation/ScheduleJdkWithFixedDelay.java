package io.koraframework.scheduling.jdk.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.temporal.ChronoUnit;

/**
 * An annotation for scheduling a re-occurring task with fixed delay between each task.
 *
 * <p>A job declared with {@code config} can be disabled by the {@code enabled} key of its configuration,
 * for example {@code jobs.my-job.enabled = false}, see
 * {@link io.koraframework.scheduling.common.SchedulingJobConfig#enabled()}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface ScheduleJdkWithFixedDelay {

    /**
     * @return initial delay in {@link #unit()} before scheduling is started
     */
    long initialDelay() default 0;

    /**
     * @return time in {@link #unit()} that have to elapse before next tasks will be scheduled
     */
    long delay() default 0;

    /**
     * @return unit to use for {@link #delay()} interpretation
     */
    ChronoUnit unit() default ChronoUnit.MILLIS;

    /**
     * @return path for configuration to apply options (config > annotation options in priority)
     */
    String config() default "";
}
