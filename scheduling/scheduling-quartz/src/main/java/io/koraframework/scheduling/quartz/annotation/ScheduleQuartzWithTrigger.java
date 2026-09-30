package io.koraframework.scheduling.quartz.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.CLASS)
public @interface ScheduleQuartzWithTrigger {

    /**
     * @return tag that specifies which {@link org.quartz.Trigger} to use for scheduling
     */
    Class<?> value();
}
