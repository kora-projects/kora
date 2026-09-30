package io.koraframework.scheduling.symbol.processor.jdk

import io.koraframework.scheduling.jdk.annotation.ScheduleJdkOnce
import java.time.temporal.ChronoUnit

class ScheduledJdkOnceTest {
    @ScheduleJdkOnce(delay = 100, config = "baseline", unit = ChronoUnit.SECONDS)
    fun baseline() {
    }

    @ScheduleJdkOnce(delay = 100, unit = ChronoUnit.SECONDS)
    fun noConfig() {
    }

    @ScheduleJdkOnce(config = "onlyConfig")
    fun onlyConfig() {
    }

    @ScheduleJdkOnce(delay = 1000)
    fun onlyRequired() {
    }

    @ScheduleJdkOnce(delay = 1000, config = "onlyRequiredWithConfig")
    fun onlyRequiredWithConfig() {
    }
}
