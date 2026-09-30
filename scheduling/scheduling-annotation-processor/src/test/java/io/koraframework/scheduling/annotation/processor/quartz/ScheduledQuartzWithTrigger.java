package io.koraframework.scheduling.annotation.processor.quartz;

import org.quartz.JobExecutionContext;
import io.koraframework.scheduling.quartz.annotation.ScheduleQuartzWithTrigger;

public class ScheduledQuartzWithTrigger {
    @ScheduleQuartzWithTrigger(ScheduledQuartzWithTrigger.class)
    public void noArgs() {}

    @ScheduleQuartzWithTrigger(ScheduledQuartzWithTrigger.class)
    public void withCtx(JobExecutionContext jobExecutionContext) {}
}
