package ru.tinkoff.kora.scheduling.annotation.processor.controller;

import ru.tinkoff.kora.scheduling.jdk.annotation.ScheduleAtFixedRate;

import java.time.temporal.ChronoUnit;

public class ScheduledJdkAtFixedRateTest {
    @ScheduleAtFixedRate(initialDelay = 100, period = 1000, config = "baseline", unit = ChronoUnit.MILLIS)
    public void baseline() {

    }

    @ScheduleAtFixedRate(initialDelay = 100, period = 1000, unit = ChronoUnit.MILLIS)
    public void noConfig() {

    }

    @ScheduleAtFixedRate(config = "onlyConfig")
    public void onlyConfig() {

    }

    @ScheduleAtFixedRate(period = 1000)
    public void onlyRequired() {

    }

    @ScheduleAtFixedRate(period = 1000, config = "onlyRequiredWithConfig")
    public void onlyRequiredWithConfig() {

    }
}
