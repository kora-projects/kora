package io.koraframework.scheduling.quartz;

import io.koraframework.application.graph.Lifecycle;
import io.koraframework.application.graph.RefreshListener;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.common.util.TimeUtils;
import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class KoraQuartzJobRegistrar implements Lifecycle, RefreshListener {

    /**
     * Group of the Quartz jobs registered by Kora.
     */
    public static final String JOB_GROUP = "kora";

    private static final Logger logger = LoggerFactory.getLogger(KoraQuartzJobRegistrar.class);

    private final Iterable<ValueOf<KoraQuartzJob>> quartzJobList;
    private final Scheduler scheduler;
    private final QuartzConfig config;

    public KoraQuartzJobRegistrar(Iterable<ValueOf<KoraQuartzJob>> quartzJobList, Scheduler scheduler, QuartzConfig config) {
        this.quartzJobList = quartzJobList;
        this.scheduler = scheduler;
        this.config = config;
    }

    /**
     * @return name of the Quartz job registered for the Kora job class
     */
    static String jobName(Class<?> jobClass) {
        var name = jobClass.getCanonicalName();
        return name != null ? name : jobClass.getName();
    }

    static JobKey jobKey(Class<?> jobClass) {
        return JobKey.jobKey(jobName(jobClass), JOB_GROUP);
    }

    private static final class QuartzJobException extends Exception {

        private final Class<?> job;

        public QuartzJobException(Class<?> job, SchedulerException cause) {
            super(cause);
            this.job = job;
        }

        public Class<?> getJob() {
            return job;
        }
    }

    @Override
    public final void init() {
        try {
            var quartzJobsNames = new ArrayList<String>();
            for (var q : quartzJobList) {
                quartzJobsNames.add(jobName(q.get().getClass()));
            }
            logger.debug("Quartz Jobs {} starting...", quartzJobsNames);
            var started = System.nanoTime();

            this.scheduleJobs();

            logger.info("Quartz Jobs {} started in {}", quartzJobsNames, TimeUtils.tookForLogging(started));
        } catch (QuartzJobException e) {
            throw new IllegalStateException("Quartz job '%s' failed to start: %s; check job triggers and Quartz scheduler configuration".formatted(jobName(e.getJob()), e.getCause().getMessage()), e.getCause());
        }
    }

    @Override
    public void graphRefreshed() throws Exception {
        this.scheduleJobs();
    }

    private void scheduleJobs() throws QuartzJobException {
        checkUniqueTriggers();
        for (var valueOf : this.quartzJobList) {
            var koraQuartzJob = valueOf.get();
            var jobClass = koraQuartzJob.getClass();
            try {
                this.moveLegacyJob(jobClass);
                var job = JobBuilder.newJob(jobClass)
                    .withIdentity(jobKey(jobClass))
                    .storeDurably()
                    .build();

                if (this.scheduler.checkExists(job.getKey())) {
                    var existingJob = this.scheduler.getJobDetail(job.getKey());
                    if (!existingJob.getJobClass().equals(jobClass) || !existingJob.isDurable()) {
                        this.scheduler.addJob(job, true);
                    }
                } else {
                    this.scheduler.addJob(job, true);
                }
                if (koraQuartzJob.getTriggers().isEmpty()) {
                    logger.info("Quartz Job '{}' has no triggers and is not scheduled", jobName(jobClass));
                }
                var existingTriggers = this.scheduler.getTriggersOfJob(job.getKey())
                    .stream()
                    .collect(Collectors.toMap(Trigger::getKey, Function.identity()));
                for (var newTrigger : koraQuartzJob.getTriggers()) {
                    var existsTrigger = existingTriggers.remove(newTrigger.getKey());
                    if (existsTrigger != null) {
                        if (triggersEqual(existsTrigger, newTrigger)) {
                            continue;
                        }
                        this.scheduler.unscheduleJob(existsTrigger.getKey());
                    }
                    var triggerToSchedule = newTrigger.getTriggerBuilder()
                        .forJob(job)
                        .build();
                    this.scheduler.scheduleJob(triggerToSchedule);
                }
                for (var entry : existingTriggers.entrySet()) {
                    this.scheduler.unscheduleJob(entry.getKey());
                }
            } catch (SchedulerException e) {
                throw new QuartzJobException(jobClass, e);
            }
        }
    }

    /**
     * Kora 1.x registered jobs in the {@code DEFAULT} group. Such a job is removed together with its triggers,
     * so that the triggers can be registered again for the job of the {@link #JOB_GROUP} group.
     */
    private void moveLegacyJob(Class<?> jobClass) throws SchedulerException {
        var legacyKey = JobKey.jobKey(jobName(jobClass));
        if (this.scheduler.checkExists(legacyKey) && this.scheduler.deleteJob(legacyKey)) {
            logger.info("Quartz Job '{}' moved from the {} group to the {} group", jobName(jobClass), JobKey.DEFAULT_GROUP, JOB_GROUP);
        }
    }

    private void checkUniqueTriggers() {
        var owners = new LinkedHashMap<TriggerKey, Class<?>>();
        for (var valueOf : this.quartzJobList) {
            var koraQuartzJob = valueOf.get();
            for (var trigger : koraQuartzJob.getTriggers()) {
                var owner = owners.putIfAbsent(trigger.getKey(), koraQuartzJob.getClass());
                if (owner != null) {
                    throw new IllegalStateException(("Quartz trigger '%s' is declared more than once, by jobs '%s' and '%s'; "
                        + "trigger identities must be unique, set a unique identity in @ScheduleQuartzWithCron(identity = ...) "
                        + "or in TriggerBuilder.withIdentity(...)").formatted(trigger.getKey(), jobName(owner), jobName(koraQuartzJob.getClass())));
                }
            }
        }
    }

    private boolean triggersEqual(Trigger oldTrigger, Trigger newTrigger) {
        if (oldTrigger.getClass() != newTrigger.getClass()) {
            return false;
        }
        // a trigger built without startAt() starts at the moment it is built, see QuartzConfig#compareStartTime()
        if (this.config.compareStartTime() && !Objects.equals(oldTrigger.getStartTime(), newTrigger.getStartTime())) return false;
        if (!Objects.equals(oldTrigger.getEndTime(), newTrigger.getEndTime())) return false;
        if (oldTrigger instanceof CronTrigger oldCron && newTrigger instanceof CronTrigger newCron) {
            return oldCron.getCronExpression().equals(newCron.getCronExpression())
                && Objects.equals(oldCron.getTimeZone(), newCron.getTimeZone());
        }
        if (oldTrigger instanceof SimpleTrigger oldSimple && newTrigger instanceof SimpleTrigger newSimple) {
            if (oldSimple.getRepeatCount() != newSimple.getRepeatCount()) return false;
            if (oldSimple.getRepeatInterval() != newSimple.getRepeatInterval()) return false;
            return true;
        }
        // user should deal with those
        return true;
    }

    @Override
    public final void release() {

    }
}
