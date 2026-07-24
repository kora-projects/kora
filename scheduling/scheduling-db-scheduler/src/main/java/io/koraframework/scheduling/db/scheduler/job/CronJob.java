package io.koraframework.scheduling.db.scheduler.job;

import com.github.kagkarlsson.scheduler.task.helper.RecurringTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import com.github.kagkarlsson.scheduler.task.schedule.CronSchedule;
import io.koraframework.scheduling.common.telemetry.SchedulingTelemetry;

public final class CronJob extends AbstractJob {

    private final RecurringTask<Void> task;

    public CronJob(SchedulingTelemetry telemetry, Runnable command, String name, String cron) {
        super(telemetry, command, name);
        var schedule = new CronSchedule(cron);
        this.task = Tasks.recurring(name, schedule).execute((instance, context) -> this.runJob());
    }

    @Override
    public RecurringTask<Void> task() {
        return this.task;
    }
}
