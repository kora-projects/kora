package io.koraframework.scheduling.ksp.controller

import org.quartz.JobExecutionContext
import io.koraframework.scheduling.quartz.ScheduleWithTrigger

class ScheduledWithTrigger {
    @ScheduleWithTrigger(ScheduledWithTrigger::class)
    fun noArgs() {
    }

    @ScheduleWithTrigger(ScheduledWithTrigger::class)
    fun withCtx(jobExecutionContext: JobExecutionContext?) {
    }
}
