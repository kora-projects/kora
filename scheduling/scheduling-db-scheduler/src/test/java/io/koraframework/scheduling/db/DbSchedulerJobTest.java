package io.koraframework.scheduling.db;

import com.github.kagkarlsson.scheduler.task.OnStartup;
import com.github.kagkarlsson.scheduler.task.helper.CustomTask;
import com.github.kagkarlsson.scheduler.task.helper.RecurringTask;
import io.koraframework.scheduling.common.telemetry.SchedulingTelemetry;
import io.koraframework.scheduling.common.telemetry.impl.NoopSchedulingObservation;
import io.koraframework.scheduling.db.scheduler.job.CronJob;
import io.koraframework.scheduling.db.scheduler.job.FixedDelayJob;
import io.koraframework.scheduling.db.scheduler.job.RunOnceJob;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class DbSchedulerJobTest {

    @Test
    void cronJobCreatesRecurringTaskAndRunsCommandWithTelemetry() {
        var calls = new AtomicInteger();
        var telemetry = telemetry();
        var job = new CronJob(telemetry, calls::incrementAndGet, "cron-job", "*/10 * * * * *");

        assertThat(job.task()).isInstanceOf(RecurringTask.class);
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
        assertThat(job.task().getName()).isEqualTo("fixed-delay-job");

        job.task().execute(job.task().instance("default"), null);

        assertThat(calls).hasValue(1);
        Mockito.verify(telemetry).observe();
    }

    @Test
    void runOnceJobCreatesStartupCustomTaskAndRunsCommandWithTelemetry() {
        var calls = new AtomicInteger();
        var telemetry = telemetry();
        var job = new RunOnceJob(telemetry, calls::incrementAndGet, "once-job", Duration.ofSeconds(3));

        assertThat(job.task()).isInstanceOf(CustomTask.class);
        assertThat(job.task()).isInstanceOf(OnStartup.class);
        assertThat(job.task().getName()).isEqualTo("once-job");

        job.task().execute(job.task().instance("once-job"), null);

        assertThat(calls).hasValue(1);
        Mockito.verify(telemetry).observe();
    }

    private static SchedulingTelemetry telemetry() {
        var telemetry = Mockito.mock(SchedulingTelemetry.class);
        when(telemetry.observe()).thenReturn(NoopSchedulingObservation.INSTANCE);
        return telemetry;
    }
}
