package ru.tinkoff.kora.scheduling.quartz;

import java.lang.annotation.*;

/**
 * An annotation that marks a @Scheduled* annotated method as one that makes updates to its
 * {@link org.quartz.JobDataMap} during execution, and wishes the scheduler to re-store the
 * <code>JobDataMap</code> when execution completes.
 *
 * <p>Jobs that are marked with this annotation should also seriously consider
 * using the {@link DisallowConcurrentExecution} annotation, to avoid data
 * storage race conditions with concurrently executing job instances.</p>
 *
 * @see DisallowConcurrentExecution
 * @see org.quartz.PersistJobDataAfterExecution
 * @see org.quartz.DisallowConcurrentExecution
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface PersistJobDataAfterExecution {
}
