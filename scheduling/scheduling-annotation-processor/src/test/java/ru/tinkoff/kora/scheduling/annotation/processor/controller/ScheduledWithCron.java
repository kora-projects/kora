package ru.tinkoff.kora.scheduling.annotation.processor.controller;

import org.quartz.JobExecutionContext;
import ru.tinkoff.kora.scheduling.quartz.ScheduleWithCron;

public class ScheduledWithCron {
    @ScheduleWithCron("i can't cron")
    public void noArgs() {}

    @ScheduleWithCron("i can't cron")
    public void withCtx(JobExecutionContext jobExecutionContext) {}

    @ScheduleWithCron(value = "i can't cron", identity = "someIdentity")
    public void withIdentity() {}

    @ScheduleWithCron(value = "i can't cron", config = "some config")
    public void withConfig() {}

}
