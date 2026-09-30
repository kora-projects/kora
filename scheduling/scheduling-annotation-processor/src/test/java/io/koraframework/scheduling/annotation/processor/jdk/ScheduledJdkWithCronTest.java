package io.koraframework.scheduling.annotation.processor.jdk;

import io.koraframework.scheduling.jdk.annotation.ScheduleJdkWithCron;

public class ScheduledJdkWithCronTest {
    @ScheduleJdkWithCron(value = "*/10 * * * * *", config = "baseline")
    public void baseline() {}

    @ScheduleJdkWithCron("*/10 * * * * *")
    public void noConfig() {}

    @ScheduleJdkWithCron(config = "onlyConfig")
    public void onlyConfig() {}
}
