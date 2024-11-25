package ru.tinkoff.kora.scheduling.quartz;

import org.quartz.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.RefreshListener;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.util.TimeUtils;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class KoraQuartzJobRegistrar implements Lifecycle, RefreshListener {

    private static final Logger logger = LoggerFactory.getLogger(KoraQuartzJobRegistrar.class);

    private final List<ValueOf<KoraQuartzJob>> quartzJobList;
    private final Scheduler scheduler;

    public KoraQuartzJobRegistrar(List<ValueOf<KoraQuartzJob>> quartzJobList, Scheduler scheduler) {
        this.quartzJobList = quartzJobList;
        this.scheduler = scheduler;
    }

    @Override
    public final void init() throws SchedulerException {
        var quartzJobsNames = quartzJobList.stream()
            .map(q -> q.get().getClass().getCanonicalName())
            .toList();

        logger.debug("Quartz Jobs {} starting...", quartzJobsNames);
        var started = System.nanoTime();

        this.scheduleJobs();

        logger.info("Quartz Jobs {} started in {}", quartzJobsNames, TimeUtils.tookForLogging(started));
    }

    @Override
    public void graphRefreshed() throws Exception {
        this.scheduleJobs();
    }

    private void scheduleJobs() throws SchedulerException {
        for (var valueOf : this.quartzJobList) {
            var koraQuartzJob = valueOf.get();
            var job = JobBuilder.newJob(koraQuartzJob.getClass())
                .withIdentity(koraQuartzJob.getClass().getCanonicalName())
                .storeDurably()
                .build();

            if (this.scheduler.checkExists(job.getKey())) {
                var existingJob = this.scheduler.getJobDetail(job.getKey());
                if (!existingJob.getJobClass().equals(koraQuartzJob.getClass()) || !existingJob.isDurable()) {
                    this.scheduler.addJob(job, true);
                }
            } else {
                this.scheduler.addJob(job, true);
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
            for (var entry: existingTriggers.entrySet()) {
                this.scheduler.unscheduleJob(entry.getKey());
            }
        }
    }

    private boolean triggersEqual(Trigger oldTrigger, Trigger newTrigger) {
        if (oldTrigger.getClass() != newTrigger.getClass()) {
            return false;
        }
        if (!Objects.equals(oldTrigger.getStartTime(), newTrigger.getStartTime())) return false;
        if (!Objects.equals(oldTrigger.getEndTime(), newTrigger.getEndTime())) return false;
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
