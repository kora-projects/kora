package io.koraframework.scheduling.db.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.temporal.ChronoUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface ScheduleWithFixedDelay {
    long initialDelay() default 0;

    long delay() default 0;

    ChronoUnit unit() default ChronoUnit.MILLIS;

    String name() default "";

    String config() default "";
}
