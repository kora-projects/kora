package io.koraframework.scheduling.symbol.processor.quartz

import org.quartz.JobExecutionContext
import io.koraframework.scheduling.quartz.annotation.ScheduleQuartzWithCron

class ScheduledQuartzWithCron {
    @ScheduleQuartzWithCron("0 0 12 * * ?")
    fun noArgs() {
    }

    @ScheduleQuartzWithCron("0 0 12 * * ?")
    fun withCtx(jobExecutionContext: JobExecutionContext?) {
    }

    @ScheduleQuartzWithCron(value = "0 0 12 * * ?", identity = "someIdentity")
    fun withIdentity() {
    }

    @ScheduleQuartzWithCron(value = "0 0 12 * * ?", config = "some config")
    fun withConfig() {
    }
}
