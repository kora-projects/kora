package io.koraframework.scheduling.symbol.processor.jdk

import io.koraframework.scheduling.jdk.annotation.ScheduleJdkWithCron

class ScheduledJdkWithCronTest {
    @ScheduleJdkWithCron(value = "*/10 * * * * *", config = "baseline")
    fun baseline() {
    }

    @ScheduleJdkWithCron("*/10 * * * * *")
    fun noConfig() {
    }

    @ScheduleJdkWithCron(config = "onlyConfig")
    fun onlyConfig() {
    }
}
