package io.koraframework.scheduling.db.scheduler.job;

import com.github.kagkarlsson.scheduler.task.Task;

/**
 * A Kora database scheduler job adapter.
 *
 * <p>Implementations expose a db-scheduler {@link Task} that can be registered
 * in {@code io.koraframework.scheduling.db.scheduler.DbSchedulerWrapper}. The
 * wrapper collects all {@code DbSchedulerJob} components from the application
 * graph, extracts their tasks, and passes them to db-scheduler during
 * application startup.
 *
 * <p>Most applications do not need to implement this interface manually.
 * Methods annotated with
 * {@code io.koraframework.scheduling.db.scheduler.annotation.ScheduleWithCron},
 * {@code io.koraframework.scheduling.db.scheduler.annotation.ScheduleWithFixedDelay}
 * or {@code io.koraframework.scheduling.db.scheduler.annotation.ScheduleOnce}
 * are processed into {@code DbSchedulerJob} components automatically.
 *
 * <p>Manual implementations are useful when a job needs direct access to
 * db-scheduler APIs, custom task descriptors, task data, custom completion
 * handling, or behavior that is not expressible through scheduling
 * annotations.
 *
 * <p>If {@link #task()} returns a task that implements
 * {@code com.github.kagkarlsson.scheduler.task.OnStartup}, the wrapper
 * registers it through db-scheduler startup task registration. Other tasks are
 * registered as regular scheduler tasks.
 *
 * <p>The task name must be stable between deployments. db-scheduler stores and
 * coordinates executions by task name, so renaming a task changes its persisted
 * identity.
 */
public interface DbSchedulerJob {

    /**
     * Returns the db-scheduler task represented by this Kora job.
     *
     * <p>The returned task is registered once when the Kora application starts.
     * It should be reusable as task metadata and must not depend on per-run
     * mutable state outside db-scheduler execution context.
     *
     * @return task to register in the database scheduler
     */
    Task<?> task();
}
