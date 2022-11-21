package ru.tinkoff.kora.scheduling.jdk.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.temporal.ChronoUnit;

/**
 * An annotation for scheduling single task with fixed delay before each task.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface ScheduleOnce {

    /**
     * @return time in {@link #unit()} that have to elapse before single task will be scheduled
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
