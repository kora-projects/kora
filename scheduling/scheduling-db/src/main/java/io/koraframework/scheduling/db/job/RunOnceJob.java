package io.koraframework.scheduling.db.job;

import com.github.kagkarlsson.scheduler.SchedulerClient;
import com.github.kagkarlsson.scheduler.task.helper.OneTimeTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import io.koraframework.scheduling.common.telemetry.SchedulingTelemetry;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class RunOnceJob extends AbstractJob {

    private final OneTimeTask<Void> task;
    private final Duration delay;

    public RunOnceJob(SchedulingTelemetry telemetry, Runnable command, String name, Duration delay) {
        super(telemetry, command, name);
        this.delay = Objects.requireNonNull(delay);
        this.task = Tasks.oneTime(name).execute((instance, context) -> this.runJob());
    }

    @Override
    public OneTimeTask<Void> task() {
        return this.task;
    }

    @Override
    public void scheduleOnStart(SchedulerClient scheduler) {
        scheduler.scheduleIfNotExists(this.task.instance(this.name), Instant.now().plus(this.delay));
    }
}
