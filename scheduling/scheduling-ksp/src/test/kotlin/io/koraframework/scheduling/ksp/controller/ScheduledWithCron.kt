package io.koraframework.scheduling.ksp.controller

import org.quartz.JobExecutionContext
import io.koraframework.scheduling.quartz.ScheduleWithCron

class ScheduledWithCron {
    @ScheduleWithCron("i can't cron")
    fun noArgs() {
    }

    @ScheduleWithCron("i can't cron")
    fun withCtx(jobExecutionContext: JobExecutionContext?) {
    }

    @ScheduleWithCron(value = "i can't cron", identity = "someIdentity")
    fun withIdentity() {
    }

    @ScheduleWithCron(value = "i can't cron", config = "some config")
    fun withConfig() {
    }
}
