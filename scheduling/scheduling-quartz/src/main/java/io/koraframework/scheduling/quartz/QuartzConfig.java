package io.koraframework.scheduling.quartz;

import io.koraframework.config.common.annotation.ConfigMapper;

import java.time.Duration;

/**
 * Configuration of the Kora Quartz integration, read from the {@code scheduling.quartz} path.
 */
@ConfigMapper
public interface QuartzConfig {

    /**
     * Maximum time to wait for running jobs on scheduler shutdown.
     *
     * <p>On shutdown the scheduler stops firing triggers and waits up to this time for running jobs to complete.
     * Jobs still running after that are interrupted, and the scheduler waits up to this time once more, so the
     * whole shutdown may take up to twice this value. A job observes the interruption through
     * {@link Thread#isInterrupted()} or an {@link InterruptedException} from a blocking call.
     *
     * <p>{@code 0s} interrupts running jobs immediately. To wait until all jobs complete regardless of time,
     * set a large value such as {@code 365d}, and make sure the platform termination grace period, for example
     * {@code terminationGracePeriodSeconds} in Kubernetes, is not shorter.
     *
     * @return time to wait for running jobs before and after interrupting them
     */
    default Duration shutdownWait() {
        return Duration.ofSeconds(30);
    }

    /**
     * Whether to remove persisted Kora jobs that are no longer present in the application graph on scheduler startup.
     *
     * <p>With a persistent job store, a job of a deleted or renamed class stays in the store, and Quartz fails with
     * {@code JobPersistenceException: Couldn't retrieve job because a required class was not found} on every startup.
     * Cleanup removes such jobs with their triggers before the scheduler starts.
     *
     * <p>Only jobs registered by Kora are removed: jobs of the {@code kora} group, see
     * {@link KoraQuartzJobRegistrar#JOB_GROUP}, and jobs of the {@code DEFAULT} group named like Kora generated jobs
     * ({@code $Type_method_Job}), which Kora 1.x registered there. Jobs added to the {@link org.quartz.Scheduler}
     * directly are kept.
     *
     * <p>Enable it only when the job store is used by instances of a single application. The cleanup removes every
     * Kora job the starting instance does not know, so it is unsafe when:
     * <ul>
     *     <li>several applications share the Quartz tables with the same scheduler name, which is
     *     {@code kora-quartz-scheduler} by default: each application removes the jobs of the others;</li>
     *     <li>instances of different versions run at once, for example during a rolling deployment: an instance of
     *     the previous version started after the new one removes the jobs added in the new version, and they return
     *     only after a restart of an instance of the new version.</li>
     * </ul>
     *
     * @return whether to remove Kora jobs absent from the application graph on startup
     */
    default boolean cleanupOrphanedJobs() {
        return false;
    }

    /**
     * Whether a change of the trigger start time reschedules a persisted trigger on startup.
     *
     * <p>Quartz sets the start time of a trigger built without {@code startAt()} to the moment the trigger is built,
     * so it differs on every application start. Comparing it would reschedule every persisted trigger on every start,
     * losing misfire handling for executions missed while the application was down and shifting the phase of simple
     * triggers. By default the start time is ignored and a persisted trigger is rescheduled only when its schedule
     * (cron expression, repeat interval or repeat count) or its end time changes.
     *
     * <p>Enable it only if all triggers use a fixed {@code startAt()} and changing it must reschedule the trigger.
     *
     * @return whether a changed trigger start time reschedules a persisted trigger
     */
    default boolean compareStartTime() {
        return false;
    }
}
