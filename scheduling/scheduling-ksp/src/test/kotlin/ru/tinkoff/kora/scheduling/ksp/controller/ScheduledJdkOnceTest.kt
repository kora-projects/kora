package ru.tinkoff.kora.scheduling.ksp.controller

import ru.tinkoff.kora.scheduling.jdk.annotation.ScheduleOnce
import java.time.temporal.ChronoUnit

class ScheduledJdkOnceTest {
    @ScheduleOnce(delay = 100, config = "baseline", unit = ChronoUnit.SECONDS)
    fun baseline() {
    }

    @ScheduleOnce(delay = 100, unit = ChronoUnit.SECONDS)
    fun noConfig() {
    }

    @ScheduleOnce(config = "onlyConfig")
    fun onlyConfig() {
    }

    @ScheduleOnce(delay = 1000)
    fun onlyRequired() {
    }

    @ScheduleOnce(delay = 1000, config = "onlyRequiredWithConfig")
    fun onlyRequiredWithConfig() {
    }
}
