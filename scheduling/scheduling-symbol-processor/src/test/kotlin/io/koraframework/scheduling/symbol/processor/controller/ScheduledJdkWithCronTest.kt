package io.koraframework.scheduling.symbol.processor.controller

import io.koraframework.scheduling.jdk.annotation.ScheduleWithCron

class ScheduledJdkWithCronTest {
    @ScheduleWithCron(value = "*/10 * * * * *", config = "baseline")
    fun baseline() {
    }

    @ScheduleWithCron("*/10 * * * * *")
    fun noConfig() {
    }

    @ScheduleWithCron(config = "onlyConfig")
    fun onlyConfig() {
    }
}
