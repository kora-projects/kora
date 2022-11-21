package ru.tinkoff.kora.scheduling.annotation.processor.controller;

import ru.tinkoff.kora.scheduling.jdk.annotation.ScheduleOnce;

import java.time.temporal.ChronoUnit;

public class ScheduledJdkOnceTest {
    @ScheduleOnce(delay = 100, config = "baseline", unit = ChronoUnit.SECONDS)
    public void baseline() {

    }

    @ScheduleOnce(delay = 100, unit = ChronoUnit.SECONDS)
    public void noConfig() {

    }

    @ScheduleOnce(config = "onlyConfig")
    public void onlyConfig() {

    }

    @ScheduleOnce(delay = 1000)
    public void onlyRequired() {

    }

    @ScheduleOnce(delay = 1000, config = "onlyRequiredWithConfig")
    public void onlyRequiredWithConfig() {

    }
}
