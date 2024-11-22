package ru.tinkoff.kora.scheduling.quartz;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.Trigger;
import org.slf4j.MDC;
import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.scheduling.common.telemetry.SchedulingTelemetry;

import java.util.List;
import java.util.function.Consumer;

public abstract class KoraQuartzJob implements Job {
    private final Consumer<JobExecutionContext> job;
    private final List<Trigger> trigger;
    private final SchedulingTelemetry telemetry;

    public KoraQuartzJob(SchedulingTelemetry telemetry, Consumer<JobExecutionContext> job, Trigger trigger) {
        this.job = job;
        this.trigger = List.of(trigger);
        this.telemetry = telemetry;
    }

    public KoraQuartzJob(SchedulingTelemetry telemetry, Consumer<JobExecutionContext> job, List<Trigger> trigger) {
        this.job = job;
        this.trigger = trigger;
        this.telemetry = telemetry;
    }

    @Override
    public final void execute(JobExecutionContext jobExecutionContext) {
        MDC.clear();
        Context.clear();

        var ctx = Context.current();
        var telemetryCtx = this.telemetry.get(ctx);
        try {
            this.job.accept(jobExecutionContext);
            telemetryCtx.close(null);
        } catch (Throwable e) {
            telemetryCtx.close(e);
            throw e;
        }
    }

    public Trigger getTrigger() {
        return this.trigger.get(0);
    }

    public List<Trigger> getTriggers() {
        return this.trigger;
    }
}
