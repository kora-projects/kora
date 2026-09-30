package io.koraframework.scheduling.annotation.processor.jdk;

import io.koraframework.scheduling.jdk.annotation.ScheduleJdkAtFixedRate;

import java.time.temporal.ChronoUnit;

public class ScheduledJdkAtFixedRateTest {
    @ScheduleJdkAtFixedRate(initialDelay = 100, period = 1000, config = "baseline", unit = ChronoUnit.MILLIS)
    public void baseline() {}

    @ScheduleJdkAtFixedRate(initialDelay = 100, period = 1000, unit = ChronoUnit.MILLIS)
    public void noConfig() {}

    @ScheduleJdkAtFixedRate(config = "onlyConfig")
    public void onlyConfig() {}

    @ScheduleJdkAtFixedRate(period = 1000)
    public void onlyRequired() {}

    @ScheduleJdkAtFixedRate(period = 1000, config = "onlyRequiredWithConfig")
    public void onlyRequiredWithConfig() {}
}
