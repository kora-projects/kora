package io.koraframework.scheduling.quartz;

import io.koraframework.config.common.annotation.ConfigMapper;

@ConfigMapper
public interface SchedulingQuartzConfig {

    /**
     * @return Whether to wait for tasks to complete before scheduler shutdown during graceful shutdown.
     */
    default boolean waitForJobComplete() {
        return true;
    }

    /**
     * Whether to remove from the persistent {@link org.quartz.Scheduler} store the jobs that are no longer registered in the application graph
     * (e.g. after a class with {@link ScheduleWithCron} was deleted or renamed) during scheduler startup.
     * <p>
     * When disabled, such orphaned jobs remain in the store and {@link org.quartz.Scheduler} logs {@link org.quartz.JobPersistenceException}:
     * Couldn't retrieve job because a required class was not found on every startup while its misfire handler retries.
     * <p>
     * Enable with caution: cleanup removes every job absent from the current application graph, so it can remove foreign jobs
     * if the scheduler is shared with other job sources, e.g. jobs added to the {@link org.quartz.Scheduler} directly
     * (outside {@link ScheduleWithCron}/{@link ScheduleWithTrigger}), or jobs registered by another application instance
     * in a clustered setup (for example, during a rolling deployment when one instance does not yet know about another instance's jobs).
     *
     * @return Whether to remove jobs absent from the application graph on scheduler startup.
     */
    default boolean cleanupOrphanedJobs() {
        return false;
    }

    /**
     * Whether to include the trigger's absolute start/end time in the schedule-equality check performed on startup and graph refresh.
     * <p>
     * Enabled by default to preserve the historical behavior. When disabled, a persisted trigger is only rescheduled when
     * its schedule definition (repeat interval/count or cron expression) changes. This prevents the trigger's {@code next_fire_time}
     * from being shifted on every restart when the trigger factory rebuilds {@code startAt()} relative to the current time.
     * <p>
     * Disable it if a trigger's start time is built relative to application startup (e.g. {@code startAt(now + interval)})
     * and the schedule must keep its original phase across restarts.
     *
     * @return Whether trigger start/end time changes reschedule a persisted trigger.
     */
    default boolean compareStartEndTime() {
        return true;
    }
}
