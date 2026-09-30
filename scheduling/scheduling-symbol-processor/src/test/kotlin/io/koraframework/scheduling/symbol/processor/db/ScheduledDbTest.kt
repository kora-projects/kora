package io.koraframework.scheduling.symbol.processor.db

import io.koraframework.scheduling.db.scheduler.annotation.ScheduleDbOnce
import io.koraframework.scheduling.db.scheduler.annotation.ScheduleDbWithCron
import io.koraframework.scheduling.db.scheduler.annotation.ScheduleDbWithFixedDelay
import java.time.temporal.ChronoUnit

class ScheduledDbTest {
    @ScheduleDbWithCron(value = "*/10 * * * * *", name = "db-cron", config = "jobs.cron")
    fun cron() {
    }

    @ScheduleDbWithCron(config = "jobs.cronOnlyConfig")
    fun cronOnlyConfig() {
    }

    @ScheduleDbWithFixedDelay(initialDelay = 100, delay = 1000, unit = ChronoUnit.MILLIS, config = "jobs.delay")
    fun fixedDelay() {
    }

    @ScheduleDbWithFixedDelay(config = "jobs.delayOnlyConfig")
    fun fixedDelayOnlyConfig() {
    }

    @ScheduleDbOnce(delay = 1000, config = "jobs.once")
    fun once() {
    }

    @ScheduleDbOnce(config = "jobs.onceOnlyConfig")
    fun onceOnlyConfig() {
    }
}
