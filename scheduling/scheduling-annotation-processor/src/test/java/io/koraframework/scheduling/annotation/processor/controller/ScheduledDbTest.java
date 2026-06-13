package io.koraframework.scheduling.annotation.processor.controller;

import io.koraframework.scheduling.db.annotation.ScheduleOnce;
import io.koraframework.scheduling.db.annotation.ScheduleWithCron;
import io.koraframework.scheduling.db.annotation.ScheduleWithFixedDelay;

import java.time.temporal.ChronoUnit;

public class ScheduledDbTest {
    @ScheduleWithCron(value = "*/10 * * * * *", name = "db-cron", config = "jobs.cron")
    public void cron() {}

    @ScheduleWithCron(config = "jobs.cronOnlyConfig")
    public void cronOnlyConfig() {}

    @ScheduleWithFixedDelay(initialDelay = 100, delay = 1000, unit = ChronoUnit.MILLIS, config = "jobs.delay")
    public void fixedDelay() {}

    @ScheduleWithFixedDelay(config = "jobs.delayOnlyConfig")
    public void fixedDelayOnlyConfig() {}

    @ScheduleOnce(delay = 1000, config = "jobs.once")
    public void once() {}

    @ScheduleOnce(config = "jobs.onceOnlyConfig")
    public void onceOnlyConfig() {}
}
