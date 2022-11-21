package ru.tinkoff.kora.scheduling.ksp.controller

import org.quartz.JobExecutionContext
import ru.tinkoff.kora.common.Tag
import ru.tinkoff.kora.scheduling.quartz.ScheduleWithTrigger

class ScheduledWithTrigger {
    @ScheduleWithTrigger(Tag(ScheduledWithTrigger::class))
    fun noArgs() {
    }

    @ScheduleWithTrigger(Tag(ScheduledWithTrigger::class))
    fun withCtx(jobExecutionContext: JobExecutionContext?) {
    }
}
