package ru.tinkoff.kora.scheduling.quartz;

import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;

import java.time.Duration;
import java.util.List;

public class KoraQuartzJobRegistrar implements Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(KoraQuartzJobRegistrar.class);

    private final List<KoraQuartzJob> quartzJobList;
    private final Scheduler scheduler;

    public KoraQuartzJobRegistrar(List<KoraQuartzJob> quartzJobList, Scheduler scheduler) {
        this.quartzJobList = quartzJobList;
        this.scheduler = scheduler;
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

        logger.info("Quartz Jobs {} started in {}", quartzJobsNames, Duration.ofNanos(System.nanoTime() - started).toString().substring(2).toLowerCase());
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
    }
}
