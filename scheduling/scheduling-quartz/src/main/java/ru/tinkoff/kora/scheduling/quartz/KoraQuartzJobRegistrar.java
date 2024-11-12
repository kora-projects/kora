package ru.tinkoff.kora.scheduling.quartz;

import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.util.TimeUtils;

import java.util.List;

public class KoraQuartzJobRegistrar implements Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(KoraQuartzJobRegistrar.class);

    private final List<KoraQuartzJob> quartzJobList;
    private final Scheduler scheduler;
    private final ValueOf<SchedulingQuartzConfig> config;

    public KoraQuartzJobRegistrar(List<KoraQuartzJob> quartzJobList, Scheduler scheduler) {
        this(quartzJobList, scheduler, ValueOf.valueOfNull());
    }

    public KoraQuartzJobRegistrar(List<KoraQuartzJob> quartzJobList,
                                  Scheduler scheduler,
                                  ValueOf<SchedulingQuartzConfig> config) {
        this.quartzJobList = quartzJobList;
        this.scheduler = scheduler;
        this.config = config;
    }

    @Override
    public final void init() throws SchedulerException {
        final List<String> quartzJobsNames = quartzJobList.stream()
            .map(q -> q.getClass().getCanonicalName())
            .toList();

        logger.debug("Quartz Jobs {} starting...", quartzJobsNames);
        var started = System.nanoTime();

        for (var koraQuartzJob : this.quartzJobList) {
            var job = JobBuilder.newJob(koraQuartzJob.getClass())
                .withIdentity(koraQuartzJob.getClass().getCanonicalName())
                .build();

            if (this.scheduler.checkExists(job.getKey())) {
                var newTrigger = koraQuartzJob.getTrigger();
                var existsTrigger = this.scheduler.getTrigger(newTrigger.getKey());
                if (triggersEqual(existsTrigger, newTrigger)) {
                    continue;
                }
                this.scheduler.deleteJob(job.getKey());
            }
            this.scheduler.scheduleJob(job, koraQuartzJob.getTrigger());
        }

        logger.info("Quartz Jobs {} started in {}", quartzJobsNames, TimeUtils.tookForLogging(started));
    }

    private boolean triggersEqual(Trigger oldTrigger, Trigger newTrigger) {
        if (oldTrigger.getClass() != newTrigger.getClass()) {
            return false;
        }
        if (!oldTrigger.getStartTime().equals(newTrigger.getStartTime())) return false;
        if (!oldTrigger.getEndTime().equals(oldTrigger.getEndTime())) return false;
        if (oldTrigger instanceof CronTrigger oldCron && newTrigger instanceof CronTrigger newCron) {
            return oldCron.getCronExpression().equals(newCron.getCronExpression());
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
        final List<String> quartzJobsNames = quartzJobList.stream()
            .map(q -> q.getClass().getCanonicalName())
            .toList();

        logger.debug("Quartz Jobs {} stopping...", quartzJobsNames);
        var started = System.nanoTime();

        try {
            final boolean waitForComplete = config.get() != null && config.get().waitForJobComplete();
            if (waitForComplete) {
                logger.debug("Quartz Jobs {} awaiting graceful shutdown...", quartzJobsNames);
            }
            scheduler.shutdown(waitForComplete);
        } catch (SchedulerException e) {
            logger.warn("Quartz Jobs {} failed completing graceful shutdown", quartzJobsNames);
            e.printStackTrace();
        }

        logger.info("Quartz Jobs {} stopped in {}", quartzJobsNames, TimeUtils.tookForLogging(started));
    }
}
