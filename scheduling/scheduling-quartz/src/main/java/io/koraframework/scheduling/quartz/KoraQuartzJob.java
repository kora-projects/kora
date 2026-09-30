package io.koraframework.scheduling.quartz;

import org.quartz.InterruptableJob;
import org.quartz.JobExecutionContext;
import org.quartz.Trigger;
import org.slf4j.MDC;
import io.koraframework.common.telemetry.Observation;
import io.koraframework.common.telemetry.OpentelemetryContext;
import io.koraframework.scheduling.common.telemetry.SchedulingTelemetry;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Base class of Quartz jobs registered by Kora.
 *
 * <p>A job without triggers is registered but never fired, which is how a job disabled by configuration is
 * represented. The job is {@link InterruptableJob interruptable}: on scheduler shutdown the threads running it are
 * interrupted once {@link QuartzConfig#shutdownWait()} expires.
 */
public abstract class KoraQuartzJob implements InterruptableJob {
    private final Consumer<JobExecutionContext> job;
    private final List<Trigger> trigger;
    private final SchedulingTelemetry telemetry;
    // one component instance serves all concurrent executions of the job
    private final Set<Thread> executingThreads = ConcurrentHashMap.newKeySet();

    public KoraQuartzJob(SchedulingTelemetry telemetry, Consumer<JobExecutionContext> job, Trigger trigger) {
        this(telemetry, job, List.of(trigger));
    }

    public KoraQuartzJob(SchedulingTelemetry telemetry, Consumer<JobExecutionContext> job, List<Trigger> trigger) {
        this.job = job;
        this.trigger = trigger;
        this.telemetry = telemetry;
    }

    @Override
    public final void execute(JobExecutionContext jobExecutionContext) {
        var thread = Thread.currentThread();
        this.executingThreads.add(thread);
        try {
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
                                this.job.accept(jobExecutionContext);
                            } catch (Throwable e) {
                                observation.observeError(e);
                                throw e;
                            } finally {
                                observation.end();
                            }
                        });
                });
        } finally {
            this.executingThreads.remove(thread);
        }
    }

    /**
     * Interrupts every thread currently running this job.
     */
    @Override
    public final void interrupt() {
        for (var thread : this.executingThreads) {
            thread.interrupt();
        }
    }

    public Trigger getTrigger() {
        return this.trigger.get(0);
    }

    public List<Trigger> getTriggers() {
        return this.trigger;
    }
}
