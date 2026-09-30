package io.koraframework.scheduling.quartz;

import io.koraframework.scheduling.quartz.util.QuartzCronUtils;
import org.junit.jupiter.api.Test;
import org.quartz.CronTrigger;
import org.quartz.TriggerBuilder;

import java.time.ZoneId;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuartzCronUtilsTest {

    @Test
    void invalidCronDescribesExpectedFormat() {
        assertThatThrownBy(() -> QuartzCronUtils.cronSchedule("0 0 12 * * *", null, QuartzCronUtilsTest.class, "job"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid CRON expression '0 0 12 * * *' for Quartz job '" + QuartzCronUtilsTest.class.getCanonicalName() + "#job'")
            .hasMessageContaining("exactly one of day-of-month and day-of-week must be '?'")
            .hasMessageContaining("'0 0 15 * * ?' runs every day at 15:00")
            .hasMessageContaining("See the Javadoc of @ScheduleQuartzWithCron for details.");
    }

    @Test
    void cronIsEvaluatedInConfiguredTimeZone() {
        var zone = ZoneId.of("Pacific/Kiritimati");
        var trigger = (CronTrigger) TriggerBuilder.newTrigger()
            .withSchedule(QuartzCronUtils.cronSchedule("0 0 15 * * ?", zone, QuartzCronUtilsTest.class, "job"))
            .build();

        assertThat(trigger.getTimeZone()).isEqualTo(TimeZone.getTimeZone(zone));
    }

    @Test
    void cronIsEvaluatedInDefaultTimeZoneWithoutConfiguredOne() {
        var trigger = (CronTrigger) TriggerBuilder.newTrigger()
            .withSchedule(QuartzCronUtils.cronSchedule("0 0 15 * * ?", null, QuartzCronUtilsTest.class, "job"))
            .build();

        assertThat(trigger.getTimeZone()).isEqualTo(TimeZone.getDefault());
    }
}
