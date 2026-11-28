package io.koraframework.scheduling.db.scheduler.job;

import com.github.kagkarlsson.scheduler.task.Task;
import io.koraframework.common.telemetry.Observation;
import io.koraframework.common.telemetry.OpentelemetryContextStorage;
import io.koraframework.scheduling.common.telemetry.*;
import org.slf4j.MDC;
import io.koraframework.common.context.KoraContext;

import java.util.Objects;
import java.util.function.Supplier;

public abstract class KoraDbJob implements DbSchedulerJob {

    protected final SchedulingTelemetry telemetry;
    protected final Runnable command;
    protected final String name;

    protected KoraDbJob(SchedulingTelemetry telemetry, Runnable command, String name) {
        this.telemetry = Objects.requireNonNull(telemetry);
        this.command = Objects.requireNonNull(command);
        this.name = Objects.requireNonNull(name);
    }

    @Override
    public abstract Task<?> task();

    protected final void runJob() {
        MDC.clear();
        var observation = this.telemetry.observe();
        var rootOtelCtx = io.opentelemetry.context.Context.root().with(observation.span());

        var baseContext = KoraContext.empty()
            .with(io.koraframework.logging.common.MDC.MDC_KEY, new io.koraframework.logging.common.MDC())
            .with(OpentelemetryContextStorage.OPENTELEMETRY_KEY, rootOtelCtx);

        var finalContext = Observation.attach(baseContext, observation);

        KoraContext.runWith(finalContext, new JobTaskExecutionRunner(this, observation));
    }

    @Override
    public String toString() {
        return "'%s#%s'".formatted(this.telemetry.jobClass().getCanonicalName(), this.telemetry.jobMethod());
    }

    private static final class JobTaskExecutionRunner implements Supplier<Void> {
        private final KoraDbJob job;
        private final SchedulingObservation observation;

        public JobTaskExecutionRunner(KoraDbJob job, SchedulingObservation observation) {
            this.job = job;
            this.observation = observation;
        }

        @Override
        public Void get() {
            observation.observeRun();
            try {
                job.command.run();
            } catch (Throwable e) {
                observation.observeError(e);
                throw e;
            } finally {
                observation.end();
            }
            return null;
        }
    }
}
