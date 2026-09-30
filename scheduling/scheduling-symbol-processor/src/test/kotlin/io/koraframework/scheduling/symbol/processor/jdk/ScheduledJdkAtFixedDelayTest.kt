package io.koraframework.scheduling.symbol.processor.jdk

import io.koraframework.scheduling.jdk.annotation.ScheduleJdkWithFixedDelay
import java.time.temporal.ChronoUnit

class ScheduledJdkAtFixedDelayTest {
    @ScheduleJdkWithFixedDelay(initialDelay = 100, delay = 1000, config = "baseline", unit = ChronoUnit.MILLIS)
    fun baseline() {
    }

    @ScheduleJdkWithFixedDelay(initialDelay = 100, delay = 1000, unit = ChronoUnit.MILLIS)
    fun noConfig() {
    }

    @ScheduleJdkWithFixedDelay(config = "onlyConfig")
    fun onlyConfig() {
    }

    @ScheduleJdkWithFixedDelay(delay = 1000)
    fun onlyRequired() {
    }

    @ScheduleJdkWithFixedDelay(delay = 1000, config = "onlyRequiredWithConfig")
    fun onlyRequiredWithConfig() {
    }
}
