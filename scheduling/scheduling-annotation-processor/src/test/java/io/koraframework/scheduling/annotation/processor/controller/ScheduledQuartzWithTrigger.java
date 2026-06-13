package io.koraframework.scheduling.annotation.processor.controller;

import org.quartz.JobExecutionContext;
import io.koraframework.scheduling.quartz.ScheduleWithTrigger;

public class ScheduledQuartzWithTrigger {
    @ScheduleWithTrigger(ScheduledQuartzWithTrigger.class)
    public void noArgs() {}

    @ScheduleWithTrigger(ScheduledQuartzWithTrigger.class)
    public void withCtx(JobExecutionContext jobExecutionContext) {}
}
