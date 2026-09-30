package io.koraframework.scheduling.db;

import io.koraframework.application.graph.All;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.scheduling.common.telemetry.SchedulingTelemetry;
import io.koraframework.scheduling.common.telemetry.impl.NoopSchedulingObservation;
import io.koraframework.scheduling.db.scheduler.DbSchedulerConfig;
import io.koraframework.scheduling.db.scheduler.KoraDbScheduler;
import io.koraframework.scheduling.db.scheduler.job.CronJob;
import io.koraframework.scheduling.db.scheduler.job.DbSchedulerJob;
import io.koraframework.scheduling.db.scheduler.job.FixedDelayJob;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import javax.sql.DataSource;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

class KoraDbSchedulerTest {

    @Test
    void jobsWithSameNameAreRejected() {
        var first = new CronJob(telemetry(FirstJobs.class, "sync"), () -> {}, "sync", "0 0 12 * * *");
        var second = new FixedDelayJob(telemetry(SecondJobs.class, "refresh"), () -> {}, "sync", Duration.ZERO, Duration.ofMinutes(1));
        DbSchedulerConfig config = () -> new DbSchedulerConfig.PollingConfig() {};
        var scheduler = new KoraDbScheduler(Mockito.mock(DataSource.class), config, All.of(valueOf(first), valueOf(second)), null);

        assertThatThrownBy(scheduler::init)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("have the same name 'sync'")
            .hasMessageContaining(FirstJobs.class.getCanonicalName() + "#sync")
            .hasMessageContaining(SecondJobs.class.getCanonicalName() + "#refresh")
            .hasMessageContaining("@ScheduleDbWithCron(name = ...)");
    }

    private static ValueOf<DbSchedulerJob> valueOf(DbSchedulerJob job) {
        return () -> job;
    }

    private static SchedulingTelemetry telemetry(Class<?> jobClass, String jobMethod) {
        var telemetry = Mockito.mock(SchedulingTelemetry.class);
        when(telemetry.observe()).thenReturn(NoopSchedulingObservation.INSTANCE);
        doReturn(jobClass).when(telemetry).jobClass();
        when(telemetry.jobMethod()).thenReturn(jobMethod);
        return telemetry;
    }

    static final class FirstJobs {}

    static final class SecondJobs {}
}
