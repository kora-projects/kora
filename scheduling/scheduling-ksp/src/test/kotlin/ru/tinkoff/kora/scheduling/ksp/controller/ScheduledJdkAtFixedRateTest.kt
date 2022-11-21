package ru.tinkoff.kora.scheduling.ksp.controller

import ru.tinkoff.kora.scheduling.jdk.annotation.ScheduleAtFixedRate
import java.time.temporal.ChronoUnit

class ScheduledJdkAtFixedRateTest {
    @ScheduleAtFixedRate(initialDelay = 100, period = 1000, config = "baseline", unit = ChronoUnit.MILLIS)
    fun baseline() {
    }

    @ScheduleAtFixedRate(initialDelay = 100, period = 1000, unit = ChronoUnit.MILLIS)
    fun noConfig() {
    }

    @ScheduleAtFixedRate(config = "onlyConfig")
    fun onlyConfig() {
    }

    @ScheduleAtFixedRate(period = 1000)
    fun onlyRequired() {
    }

    @ScheduleAtFixedRate(period = 1000, config = "onlyRequiredWithConfig")
    fun onlyRequiredWithConfig() {
    }
}
