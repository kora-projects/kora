package io.koraframework.scheduling.annotation.processor.controller;

import org.quartz.JobExecutionContext;
import io.koraframework.scheduling.quartz.ScheduleWithTrigger;

public class ScheduledWithTrigger {
    @ScheduleWithTrigger(ScheduledWithTrigger.class)
    public void noArgs() {}

    @ScheduleWithTrigger(ScheduledWithTrigger.class)
    public void withCtx(JobExecutionContext jobExecutionContext) {}
}
