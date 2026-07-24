package io.koraframework.scheduling.symbol.processor.controller

import io.koraframework.scheduling.db.scheduler.annotation.ScheduleOnce
import io.koraframework.scheduling.db.scheduler.annotation.ScheduleWithCron
import io.koraframework.scheduling.db.scheduler.annotation.ScheduleWithFixedDelay
import java.time.temporal.ChronoUnit

class ScheduledDbTest {
    @ScheduleWithCron(value = "*/10 * * * * *", name = "db-cron", config = "jobs.cron")
    fun cron() {
    }

    @ScheduleWithCron(config = "jobs.cronOnlyConfig")
    fun cronOnlyConfig() {
    }

    @ScheduleWithFixedDelay(initialDelay = 100, delay = 1000, unit = ChronoUnit.MILLIS, config = "jobs.delay")
    fun fixedDelay() {
    }

    @ScheduleWithFixedDelay(config = "jobs.delayOnlyConfig")
    fun fixedDelayOnlyConfig() {
    }

    @ScheduleOnce(delay = 1000, config = "jobs.once")
    fun once() {
    }

    @ScheduleOnce(config = "jobs.onceOnlyConfig")
    fun onceOnlyConfig() {
    }
}
