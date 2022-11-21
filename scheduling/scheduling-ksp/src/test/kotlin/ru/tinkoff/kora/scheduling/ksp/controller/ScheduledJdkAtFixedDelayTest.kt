package ru.tinkoff.kora.scheduling.ksp.controller

import ru.tinkoff.kora.scheduling.jdk.annotation.ScheduleWithFixedDelay
import java.time.temporal.ChronoUnit

class ScheduledJdkAtFixedDelayTest {
    @ScheduleWithFixedDelay(initialDelay = 100, delay = 1000, config = "baseline", unit = ChronoUnit.MILLIS)
    fun baseline() {
    }

    @ScheduleWithFixedDelay(initialDelay = 100, delay = 1000, unit = ChronoUnit.MILLIS)
    fun noConfig() {
    }

    @ScheduleWithFixedDelay(config = "onlyConfig")
    fun onlyConfig() {
    }

    @ScheduleWithFixedDelay(delay = 1000)
    fun onlyRequired() {
    }

    @ScheduleWithFixedDelay(delay = 1000, config = "onlyRequiredWithConfig")
    fun onlyRequiredWithConfig() {
    }
}
