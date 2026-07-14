package io.koraframework.scheduling.db.job;

import com.github.kagkarlsson.scheduler.task.ExecutionComplete;
import com.github.kagkarlsson.scheduler.task.helper.RecurringTask;
import com.github.kagkarlsson.scheduler.task.helper.Tasks;
import com.github.kagkarlsson.scheduler.task.schedule.FixedDelay;
import com.github.kagkarlsson.scheduler.task.schedule.Schedule;
import io.koraframework.scheduling.common.telemetry.SchedulingTelemetry;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public final class FixedDelayJob extends AbstractJob {

    private final RecurringTask<Void> task;

    public FixedDelayJob(SchedulingTelemetry telemetry, Runnable command, String name, Duration initialDelay, Duration delay) {
        super(telemetry, command, name);
        var schedule = new InitialDelaySchedule(FixedDelay.of(delay), initialDelay);
        this.task = Tasks.recurring(name, schedule).execute((instance, context) -> this.runJob());
    }

    @Override
    public RecurringTask<Void> task() {
        return this.task;
    }

    private record InitialDelaySchedule(Schedule delegate, Duration initialDelay) implements Schedule, Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        private InitialDelaySchedule {
            Objects.requireNonNull(delegate);
            Objects.requireNonNull(initialDelay);
        }

        @Override
        public Instant getInitialExecutionTime(Instant now) {
            return now.plus(this.initialDelay);
        }

        @Override
        public Instant getNextExecutionTime(ExecutionComplete executionComplete) {
            return this.delegate.getNextExecutionTime(executionComplete);
        }

        @Override
        public boolean isDeterministic() {
            return this.delegate.isDeterministic();
        }

        @Override
        public boolean isDisabled() {
            return this.delegate.isDisabled();
        }

        @Override
        public String toString() {
            return this.delegate.toString();
        }
    }
}
