package io.koraframework.scheduling.symbol.processor.quartz

import org.quartz.JobExecutionContext
import io.koraframework.scheduling.quartz.annotation.ScheduleQuartzWithTrigger

class ScheduledQuartzWithTrigger {
    @ScheduleQuartzWithTrigger(ScheduledQuartzWithTrigger::class)
    fun noArgs() {
    }

    @ScheduleQuartzWithTrigger(ScheduledQuartzWithTrigger::class)
    fun withCtx(jobExecutionContext: JobExecutionContext?) {
    }
}
