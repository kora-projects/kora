package io.koraframework.scheduling.jdk.job;

import io.koraframework.scheduling.common.telemetry.SchedulingTelemetry;
import io.koraframework.scheduling.jdk.SchedulingJdkExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JobConfigurationTest {

    private final SchedulingTelemetry telemetry = mock(SchedulingTelemetry.class);
    private final SchedulingJdkExecutor executor = mock(SchedulingJdkExecutor.class);

    @BeforeEach
    void setup() {
        doReturn(JobConfigurationTest.class).when(this.telemetry).jobClass();
        when(this.telemetry.jobMethod()).thenReturn("job");
        doReturn(mock(ScheduledFuture.class)).when(this.executor).scheduleOnce(any(), any(Long.class), any(TimeUnit.class));
    }

    @Test
    void invalidCronDescribesExpectedFormat() {
        assertThatThrownBy(() -> new CronJob(this.telemetry, this.executor, () -> {}, "0 0 12 L * ?", null, true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid CRON expression '0 0 12 L * ?' for JDK job '" + JobConfigurationTest.class.getCanonicalName() + "#job'")
            .hasMessageContaining("┌───────────── second (0-59, optional)")
            .hasMessageContaining("'0 0 15 * * ?' runs every day at 15:00")
            .hasMessageContaining("See the Javadoc of @ScheduleJdkWithCron for details.");
    }

    @Test
    void cronIsEvaluatedInConfiguredTimeZone() {
        var zone = ZoneId.of("Pacific/Kiritimati");
        var job = new CronJob(this.telemetry, this.executor, () -> {}, "0 0 15 * * *", zone, true);

        var before = ZonedDateTime.now(zone);
        job.init();

        var delay = ArgumentCaptor.forClass(Long.class);
        verify(this.executor).scheduleOnce(any(), delay.capture(), eq(TimeUnit.NANOSECONDS));
        var next = before.truncatedTo(ChronoUnit.DAYS).withHour(15);
        if (!next.isAfter(before)) {
            next = next.plusDays(1);
        }
        assertThat(Duration.ofNanos(delay.getValue())).isCloseTo(Duration.between(before, next), Duration.ofSeconds(5));
        job.release();
    }

    @Test
    void disabledCronJobIsNotScheduled() {
        new CronJob(this.telemetry, this.executor, () -> {}, "0 0 15 * * *", null, false).init();

        verifyNoInteractions(this.executor);
    }

    @Test
    void disabledIntervalJobsAreNotScheduled() {
        new FixedDelayJob(this.telemetry, this.executor, () -> {}, Duration.ZERO, Duration.ofSeconds(1), false).init();
        new FixedRateJob(this.telemetry, this.executor, () -> {}, Duration.ZERO, Duration.ofSeconds(1), false).init();
        new RunOnceJob(this.telemetry, this.executor, () -> {}, Duration.ofSeconds(1), false).init();

        verifyNoInteractions(this.executor);
    }
}
