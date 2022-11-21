package ru.tinkoff.kora.scheduling.jdk.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.temporal.ChronoUnit;

/**
 * An annotation for scheduling a re-occurring task with fixed rate between each task.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface ScheduleAtFixedRate {

    /**
     * @return initial delay in {@link #unit()} before scheduling is started
     */
    long initialDelay() default 0;

    /**
     * @return time elapsed in {@link #unit()} between scheduled tasks
     */
    long period() default 0;

    /**
     * @return unit to use for {@link #period()} interpretation
     */
    ChronoUnit unit() default ChronoUnit.MILLIS;

    /**
     * @return path for configuration to apply options (config > annotation options in priority)
     */
    String config() default "";
}
