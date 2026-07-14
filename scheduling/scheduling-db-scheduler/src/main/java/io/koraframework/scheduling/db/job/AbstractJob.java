package io.koraframework.scheduling.db.job;

import com.github.kagkarlsson.scheduler.task.Task;
import io.koraframework.common.telemetry.Observation;
import io.koraframework.common.telemetry.OpentelemetryContext;
import io.koraframework.scheduling.common.telemetry.SchedulingTelemetry;
import org.slf4j.MDC;

import java.util.Objects;

public abstract class AbstractJob implements SchedulingDbJob {

    protected final SchedulingTelemetry telemetry;
    protected final Runnable command;
    protected final String name;

    protected AbstractJob(SchedulingTelemetry telemetry, Runnable command, String name) {
        this.telemetry = Objects.requireNonNull(telemetry);
        this.command = Objects.requireNonNull(command);
        this.name = Objects.requireNonNull(name);
    }

    @Override
    public abstract Task<?> task();

    protected final void runJob() {
        ScopedValue.where(io.koraframework.logging.common.MDC.VALUE, new io.koraframework.logging.common.MDC())
            .where(OpentelemetryContext.VALUE, io.opentelemetry.context.Context.root())
            .run(() -> {
                MDC.clear();
                var observation = this.telemetry.observe();
                ScopedValue.where(Observation.VALUE, observation)
                    .where(OpentelemetryContext.VALUE, io.opentelemetry.context.Context.root().with(observation.span()))
                    .run(() -> {
                        observation.observeRun();
                        try {
                            this.command.run();
                        } catch (Throwable e) {
                            observation.observeError(e);
                            throw e;
                        } finally {
                            observation.end();
                        }
                    });
            });
    }
}
