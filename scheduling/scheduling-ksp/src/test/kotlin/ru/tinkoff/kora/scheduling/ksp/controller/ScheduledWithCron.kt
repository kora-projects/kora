package ru.tinkoff.kora.scheduling.ksp.controller

import org.quartz.JobExecutionContext
import ru.tinkoff.kora.scheduling.quartz.ScheduleWithCron

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
