package ru.tinkoff.kora.scheduling.quartz;

import java.lang.annotation.*;

/**
 * An annotation that marks a @Scheduled* annotated method as one that must not have multiple
 * executions concurrently
 *
 * @see org.quartz.PersistJobDataAfterExecution
 * @see org.quartz.DisallowConcurrentExecution
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface DisallowConcurrentExecution {

}

