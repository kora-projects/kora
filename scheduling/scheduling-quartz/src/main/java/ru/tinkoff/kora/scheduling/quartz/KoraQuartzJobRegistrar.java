package ru.tinkoff.kora.scheduling.quartz;

import org.quartz.*;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.Lifecycle;

import java.util.List;

public class KoraQuartzJobRegistrar implements Lifecycle {
    private final List<KoraQuartzJob> quartzJobList;
    private final Scheduler scheduler;

    public KoraQuartzJobRegistrar(List<KoraQuartzJob> quartzJobList, Scheduler scheduler) {
        this.quartzJobList = quartzJobList;
        this.scheduler = scheduler;
    }

    @Override
    public final Mono<?> init() {
        return Mono.fromCallable(() -> {
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
            return null;
        });
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
    public final Mono<?> release() {
        return Mono.empty();
    }
}
