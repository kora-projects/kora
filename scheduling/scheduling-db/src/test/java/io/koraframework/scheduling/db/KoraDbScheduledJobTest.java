package io.koraframework.scheduling.db;

import com.github.kagkarlsson.scheduler.SchedulerClient;
import com.github.kagkarlsson.scheduler.task.helper.OneTimeTask;
import com.github.kagkarlsson.scheduler.task.helper.RecurringTask;
import io.koraframework.scheduling.common.telemetry.SchedulingTelemetry;
import io.koraframework.scheduling.common.telemetry.impl.NoopSchedulingObservation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;

class KoraDbScheduledJobTest {

    @Test
    void cronJobCreatesRecurringTaskAndRunsCommandWithTelemetry() {
        var calls = new AtomicInteger();
        var telemetry = telemetry();
        var job = new CronJob(telemetry, calls::incrementAndGet, "cron-job", "*/10 * * * * *");

        assertThat(job.task()).isInstanceOf(RecurringTask.class);
        assertThat(job.startupTask()).isSameAs(job.task());
        assertThat(job.task().getName()).isEqualTo("cron-job");

        job.task().execute(job.task().instance("default"), null);

        assertThat(calls).hasValue(1);
        Mockito.verify(telemetry).observe();
    }

    @Test
    void fixedDelayJobCreatesRecurringTaskAndRunsCommandWithTelemetry() {
        var calls = new AtomicInteger();
        var telemetry = telemetry();
        var job = new FixedDelayJob(telemetry, calls::incrementAndGet, "fixed-delay-job", Duration.ofSeconds(1), Duration.ofSeconds(5));

        assertThat(job.task()).isInstanceOf(RecurringTask.class);
        assertThat(job.startupTask()).isSameAs(job.task());
        assertThat(job.task().getName()).isEqualTo("fixed-delay-job");

        job.task().execute(job.task().instance("default"), null);

        assertThat(calls).hasValue(1);
        Mockito.verify(telemetry).observe();
    }

    @Test
    void runOnceJobCreatesOneTimeTaskAndSchedulesItOnStart() {
        var calls = new AtomicInteger();
        var telemetry = telemetry();
        var job = new RunOnceJob(telemetry, calls::incrementAndGet, "once-job", Duration.ofSeconds(3));

        assertThat(job.task()).isInstanceOf(OneTimeTask.class);
        assertThat(job.startupTask()).isNull();
        assertThat(job.task().getName()).isEqualTo("once-job");

        job.task().execute(job.task().instance("once-job"), null);

        assertThat(calls).hasValue(1);
        Mockito.verify(telemetry).observe();

        var scheduler = Mockito.mock(SchedulerClient.class);
        var instantCaptor = ArgumentCaptor.forClass(Instant.class);

        job.scheduleOnStart(scheduler);

        Mockito.verify(scheduler).scheduleIfNotExists(
            argThat(taskInstance -> taskInstance.getTaskName().equals("once-job") && taskInstance.getId().equals("once-job")),
            instantCaptor.capture()
        );
        assertThat(instantCaptor.getValue()).isAfter(Instant.now().minusSeconds(1));
    }

    private static SchedulingTelemetry telemetry() {
        var telemetry = Mockito.mock(SchedulingTelemetry.class);
        when(telemetry.observe()).thenReturn(NoopSchedulingObservation.INSTANCE);
        return telemetry;
    }
}
