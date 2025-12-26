package ru.tinkoff.kora.scheduling.ksp.controller

import org.quartz.JobExecutionContext
import ru.tinkoff.kora.scheduling.quartz.ScheduleWithTrigger

class ScheduledWithTrigger {
    @ScheduleWithTrigger(ScheduledWithTrigger::class)
    fun noArgs() {
    }

    @ScheduleWithTrigger(ScheduledWithTrigger::class)
    fun withCtx(jobExecutionContext: JobExecutionContext?) {
    }
}
