package io.koraframework.scheduling.db.scheduler.job;

import com.github.kagkarlsson.scheduler.task.CompletionHandler;
import com.github.kagkarlsson.scheduler.task.TaskDescriptor;
import com.github.kagkarlsson.scheduler.task.helper.CustomTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import io.koraframework.scheduling.common.telemetry.SchedulingTelemetry;

import java.time.Duration;
import java.util.Objects;

public final class RunOnceJob extends KoraDbJob {

    private final CustomTask<Void> task;

    public RunOnceJob(SchedulingTelemetry telemetry, Runnable command, String name, Duration delay) {
        this(telemetry, command, name, delay, true);
    }

    /**
     * @param enabled {@code false} when the job is disabled by the {@code enabled} key of its configuration:
     *                it is not scheduled on startup, and an execution scheduled earlier is removed without running the job
     */
    public RunOnceJob(SchedulingTelemetry telemetry, Runnable command, String name, Duration delay, boolean enabled) {
        super(telemetry, command, name);
        Objects.requireNonNull(delay);
        var builder = Tasks.custom(TaskDescriptor.of(name));
        if (enabled) {
            builder = builder.scheduleOnStartup(name, null, now -> now.plus(delay));
        }
        this.task = builder
            .onFailure((executionComplete, executionOperations) -> executionOperations.remove())
            .execute((instance, context) -> {
                if (enabled) {
                    this.runJob();
                }
                return new CompletionHandler.OnCompleteRemove<>();
            });
    }

    @Override
    public CustomTask<Void> task() {
        return this.task;
    }
}
