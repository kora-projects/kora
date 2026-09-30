package io.koraframework.scheduling.annotation.processor.jdk;

import io.koraframework.scheduling.jdk.annotation.ScheduleJdkOnce;

import java.time.temporal.ChronoUnit;

public class ScheduledJdkOnceTest {
    @ScheduleJdkOnce(delay = 100, config = "baseline", unit = ChronoUnit.SECONDS)
    public void baseline() {

    }

    @ScheduleJdkOnce(delay = 100, unit = ChronoUnit.SECONDS)
    public void noConfig() {

    }

    @ScheduleJdkOnce(config = "onlyConfig")
    public void onlyConfig() {

    }

    @ScheduleJdkOnce(delay = 1000)
    public void onlyRequired() {

    }

    @ScheduleJdkOnce(delay = 1000, config = "onlyRequiredWithConfig")
    public void onlyRequiredWithConfig() {

    }
}
