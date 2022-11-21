package ru.tinkoff.kora.scheduling.annotation.processor.controller;

import ru.tinkoff.kora.scheduling.jdk.annotation.ScheduleWithFixedDelay;

import java.time.temporal.ChronoUnit;

public class ScheduledJdkAtFixedDelayTest {
    @ScheduleWithFixedDelay(initialDelay = 100, delay = 1000, config = "baseline", unit = ChronoUnit.MILLIS)
    public void baseline() {

    }

    @ScheduleWithFixedDelay(initialDelay = 100, delay = 1000, unit = ChronoUnit.MILLIS)
    public void noConfig() {

    }

    @ScheduleWithFixedDelay(config = "onlyConfig")
    public void onlyConfig() {

    }

    @ScheduleWithFixedDelay(delay = 1000)
    public void onlyRequired() {

    }

    @ScheduleWithFixedDelay(delay = 1000, config = "onlyRequiredWithConfig")
    public void onlyRequiredWithConfig() {

    }
}
