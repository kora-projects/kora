package io.koraframework.scheduling.db.job;

import com.github.kagkarlsson.scheduler.task.CompletionHandler;
import com.github.kagkarlsson.scheduler.task.TaskDescriptor;
import com.github.kagkarlsson.scheduler.task.helper.CustomTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import io.koraframework.scheduling.common.telemetry.SchedulingTelemetry;

import java.time.Duration;
import java.util.Objects;

public final class RunOnceJob extends AbstractJob {

    private final CustomTask<Void> task;

    public RunOnceJob(SchedulingTelemetry telemetry, Runnable command, String name, Duration delay) {
        super(telemetry, command, name);
        Objects.requireNonNull(delay);
        this.task = Tasks.custom(TaskDescriptor.of(name))
            .scheduleOnStartup(name, null, now -> now.plus(delay))
            .onFailure((executionComplete, executionOperations) -> executionOperations.remove())
            .execute((instance, context) -> {
                this.runJob();
                return new CompletionHandler.OnCompleteRemove<>();
            });
    }

    @Override
    public CustomTask<Void> task() {
        return this.task;
    }
}
