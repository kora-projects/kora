package io.koraframework.scheduling.annotation.processor.quartz;

import org.quartz.JobExecutionContext;
import io.koraframework.scheduling.quartz.annotation.ScheduleQuartzWithCron;

public class ScheduledQuartzWithCron {
    @ScheduleQuartzWithCron("0 0 12 * * ?")
    public void noArgs() {}

    @ScheduleQuartzWithCron("0 0 12 * * ?")
    public void withCtx(JobExecutionContext jobExecutionContext) {}

    @ScheduleQuartzWithCron(value = "0 0 12 * * ?", identity = "someIdentity")
    public void withIdentity() {}

    @ScheduleQuartzWithCron(value = "0 0 12 * * ?", config = "some config")
    public void withConfig() {}

}
