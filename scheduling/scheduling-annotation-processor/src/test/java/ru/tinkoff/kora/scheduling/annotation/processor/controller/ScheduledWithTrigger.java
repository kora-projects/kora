package ru.tinkoff.kora.scheduling.annotation.processor.controller;

import org.quartz.JobExecutionContext;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.scheduling.quartz.ScheduleWithTrigger;

public class ScheduledWithTrigger {
    @ScheduleWithTrigger(@Tag(ScheduledWithTrigger.class))
    public void noArgs() {}

    @ScheduleWithTrigger(@Tag(ScheduledWithTrigger.class))
    public void withCtx(JobExecutionContext jobExecutionContext) {}
}
