package io.koraframework.scheduling.symbol.processor.jdk

import io.koraframework.scheduling.jdk.annotation.ScheduleJdkAtFixedRate
import java.time.temporal.ChronoUnit

class ScheduledJdkAtFixedRateTest {
    @ScheduleJdkAtFixedRate(initialDelay = 100, period = 1000, config = "baseline", unit = ChronoUnit.MILLIS)
    fun baseline() {
    }

    @ScheduleJdkAtFixedRate(initialDelay = 100, period = 1000, unit = ChronoUnit.MILLIS)
    fun noConfig() {
    }

    @ScheduleJdkAtFixedRate(config = "onlyConfig")
    fun onlyConfig() {
    }

    @ScheduleJdkAtFixedRate(period = 1000)
    fun onlyRequired() {
    }

    @ScheduleJdkAtFixedRate(period = 1000, config = "onlyRequiredWithConfig")
    fun onlyRequiredWithConfig() {
    }
}
