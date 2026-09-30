package io.koraframework.scheduling.annotation.processor.jdk;

import io.koraframework.scheduling.jdk.annotation.ScheduleJdkWithFixedDelay;

import java.time.temporal.ChronoUnit;

public class ScheduledJdkAtFixedDelayTest {
    @ScheduleJdkWithFixedDelay(initialDelay = 100, delay = 1000, config = "baseline", unit = ChronoUnit.MILLIS)
    public void baseline() {

    }

    @ScheduleJdkWithFixedDelay(initialDelay = 100, delay = 1000, unit = ChronoUnit.MILLIS)
    public void noConfig() {

    }

    @ScheduleJdkWithFixedDelay(config = "onlyConfig")
    public void onlyConfig() {

    }

    @ScheduleJdkWithFixedDelay(delay = 1000)
    public void onlyRequired() {

    }

    @ScheduleJdkWithFixedDelay(delay = 1000, config = "onlyRequiredWithConfig")
    public void onlyRequiredWithConfig() {

    }
}
