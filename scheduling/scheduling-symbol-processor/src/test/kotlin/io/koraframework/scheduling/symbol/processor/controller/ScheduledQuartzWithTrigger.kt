package io.koraframework.scheduling.symbol.processor.controller

import org.quartz.JobExecutionContext
import io.koraframework.scheduling.quartz.ScheduleWithTrigger

class ScheduledQuartzWithTrigger {
    @ScheduleWithTrigger(ScheduledQuartzWithTrigger::class)
    fun noArgs() {
    }

    @ScheduleWithTrigger(ScheduledQuartzWithTrigger::class)
    fun withCtx(jobExecutionContext: JobExecutionContext?) {
    }
}
