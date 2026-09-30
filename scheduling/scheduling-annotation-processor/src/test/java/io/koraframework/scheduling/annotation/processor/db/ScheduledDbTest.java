package io.koraframework.scheduling.annotation.processor.db;

import io.koraframework.scheduling.db.scheduler.annotation.ScheduleDbOnce;
import io.koraframework.scheduling.db.scheduler.annotation.ScheduleDbWithCron;
import io.koraframework.scheduling.db.scheduler.annotation.ScheduleDbWithFixedDelay;

import java.time.temporal.ChronoUnit;

public class ScheduledDbTest {
    @ScheduleDbWithCron(value = "*/10 * * * * *")
    public void cronNoConfig() {}

    @ScheduleDbWithCron(value = "*/10 * * * * *", name = "db-cron", config = "jobs.cron")
    public void cron() {}

    @ScheduleDbWithCron(config = "jobs.cronOnlyConfig")
    public void cronOnlyConfig() {}

    @ScheduleDbWithFixedDelay(initialDelay = 100, delay = 1000, unit = ChronoUnit.MILLIS)
    public void fixedDelayNoConfig() {}

    @ScheduleDbWithFixedDelay(initialDelay = 100, delay = 1000, unit = ChronoUnit.MILLIS, config = "jobs.delay")
    public void fixedDelay() {}

    @ScheduleDbWithFixedDelay(config = "jobs.delayOnlyConfig")
    public void fixedDelayOnlyConfig() {}

    @ScheduleDbOnce(delay = 1000, config = "jobs.once")
    public void once() {}

    @ScheduleDbOnce(config = "jobs.onceOnlyConfig")
    public void onceOnlyConfig() {}
}
