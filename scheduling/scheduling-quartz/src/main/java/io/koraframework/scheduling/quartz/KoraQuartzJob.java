package io.koraframework.scheduling.quartz;

import io.koraframework.common.context.*;
import io.koraframework.common.telemetry.*;
import io.koraframework.scheduling.common.telemetry.*;
import org.quartz.*;
import org.slf4j.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;

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
            MDC.clear();
            var observation = this.telemetry.observe();
            var rootOtelCtx = io.opentelemetry.context.Context.root().with(observation.span());

            var baseContext = KoraContext.empty()
                .with(io.koraframework.logging.common.MDC.MDC_KEY, new io.koraframework.logging.common.MDC())
                .with(OpentelemetryContextStorage.OPENTELEMETRY_KEY, rootOtelCtx);

            var finalContext = Observation.attach(baseContext, observation);

            KoraContext.runWith(finalContext, new QuartzJobExecutionRunner(this, jobExecutionContext, observation));
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

    private static final class QuartzJobExecutionRunner implements Supplier<Void> {
        private final KoraQuartzJob quartzJob;
        private final JobExecutionContext context;
        private final SchedulingObservation observation;

        public QuartzJobExecutionRunner(KoraQuartzJob quartzJob,
                                        JobExecutionContext context,
                                        SchedulingObservation observation) {
            this.quartzJob = quartzJob;
            this.context = context;
            this.observation = observation;
        }

        @Override
        public Void get() {
            observation.observeRun();
            try {
                quartzJob.job.accept(context);
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
