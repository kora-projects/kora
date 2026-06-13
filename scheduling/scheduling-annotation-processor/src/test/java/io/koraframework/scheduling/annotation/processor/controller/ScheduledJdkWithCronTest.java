package io.koraframework.scheduling.annotation.processor.controller;

import io.koraframework.scheduling.jdk.annotation.ScheduleWithCron;

public class ScheduledJdkWithCronTest {
    @ScheduleWithCron(value = "*/10 * * * * *", config = "baseline")
    public void baseline() {}

    @ScheduleWithCron("*/10 * * * * *")
    public void noConfig() {}

    @ScheduleWithCron(config = "onlyConfig")
    public void onlyConfig() {}
}
