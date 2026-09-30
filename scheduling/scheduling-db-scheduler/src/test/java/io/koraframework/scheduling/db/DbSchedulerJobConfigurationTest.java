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
import io.koraframework.scheduling.db.scheduler.job.RunOnceJob;
import io.koraframework.test.postgres.PostgresParams;
import io.koraframework.test.postgres.PostgresTestContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(PostgresTestContainer.class)
class DbSchedulerJobConfigurationTest {

    private static final DbSchedulerConfig CONFIG = new DbSchedulerConfig() {
        @Override
        public boolean tableInitialize() {
            return true;
        }

        @Override
        public PollingConfig polling() {
            return new PollingConfig() {};
        }
    };

    private DataSource dataSource;

    @BeforeEach
    void setUp(PostgresParams params) {
        var dataSource = new PGSimpleDataSource();
        dataSource.setUrl(params.jdbcUrl());
        dataSource.setUser(params.user());
        dataSource.setPassword(params.password());
        this.dataSource = dataSource;
    }

    @Test
    void invalidCronDescribesExpectedFormat() {
        assertThatThrownBy(() -> new CronJob(telemetry(), () -> {}, "sync", "0 0 12 * *", null, true))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid CRON expression '0 0 12 * *' for database scheduled job '" + DbSchedulerJobConfigurationTest.class.getCanonicalName() + "#job'")
            .hasMessageContaining("│ │ │ │ │ ┌───────────── day of the week (0-7 or MON-SUN, 0 and 7 are Sunday, L, # or ?)")
            .hasMessageContaining("'0 0 15 * * *' runs every day at 15:00")
            .hasMessageContaining("See the Javadoc of @ScheduleDbWithCron for details.");
    }

    @Test
    void cronIsEvaluatedInConfiguredTimeZone() throws Exception {
        var zone = ZoneId.of("Pacific/Kiritimati");
        var before = ZonedDateTime.now(zone);
        run(new CronJob(telemetry(), () -> {}, "sync", "0 0 15 * * *", zone, true));

        var next = before.truncatedTo(ChronoUnit.DAYS).withHour(15);
        if (!next.isAfter(before)) {
            next = next.plusDays(1);
        }
        assertThat(executionTimes()).containsExactly(next.toInstant());
    }

    @Test
    void disabledCronJobRemovesScheduledExecution() throws Exception {
        run(new CronJob(telemetry(), () -> {}, "sync", "0 0 15 * * *", null, true));
        assertThat(executionTimes()).hasSize(1);

        run(new CronJob(telemetry(), () -> {}, "sync", "0 0 15 * * *", null, false));

        assertThat(executionTimes()).isEmpty();
    }

    @Test
    void disabledFixedDelayJobRemovesScheduledExecution() throws Exception {
        run(new FixedDelayJob(telemetry(), () -> {}, "refresh", Duration.ofHours(1), Duration.ofHours(1), true));
        assertThat(executionTimes()).hasSize(1);

        run(new FixedDelayJob(telemetry(), () -> {}, "refresh", Duration.ofHours(1), Duration.ofHours(1), false));

        assertThat(executionTimes()).isEmpty();
    }

    @Test
    void disabledRunOnceJobIsNotScheduled() throws Exception {
        run(new RunOnceJob(telemetry(), () -> {}, "import", Duration.ofHours(1), false));

        assertThat(executionTimes()).isEmpty();
    }

    @Test
    void disabledRunOnceJobRemovesExecutionScheduledEarlierWithoutRunningIt() throws Exception {
        var calls = new AtomicInteger();
        run(new RunOnceJob(telemetry(), calls::incrementAndGet, "import", Duration.ofHours(1), true));
        try (var connection = this.dataSource.getConnection();
             var statement = connection.prepareStatement("UPDATE kora_scheduling_db_scheduler_jobs SET execution_time = ?")) {
            statement.setTimestamp(1, Timestamp.from(Instant.now().minusSeconds(1)));
            statement.executeUpdate();
        }

        var scheduler = start(new RunOnceJob(telemetry(), calls::incrementAndGet, "import", Duration.ofHours(1), false));
        try {
            var deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (!executionTimes().isEmpty() && System.nanoTime() < deadline) {
                Thread.sleep(100);
            }
        } finally {
            scheduler.release();
        }

        assertThat(executionTimes()).isEmpty();
        assertThat(calls).hasValue(0);
    }

    private void run(DbSchedulerJob job) throws Exception {
        start(job).release();
    }

    private KoraDbScheduler start(DbSchedulerJob job) throws Exception {
        var config = new DbSchedulerConfig() {
            @Override
            public boolean tableInitialize() {
                return true;
            }

            @Override
            public PollingConfig polling() {
                return new PollingConfig() {
                    @Override
                    public Duration interval() {
                        return Duration.ofMillis(100);
                    }
                };
            }
        };
        ValueOf<DbSchedulerJob> value = () -> job;
        var scheduler = new KoraDbScheduler(this.dataSource, config, All.of(value), null);
        scheduler.init();
        return scheduler;
    }

    private List<Instant> executionTimes() throws Exception {
        try (var connection = this.dataSource.getConnection();
             var statement = connection.createStatement();
             var rs = statement.executeQuery("SELECT execution_time FROM " + CONFIG.tableName())) {
            var result = new ArrayList<Instant>();
            while (rs.next()) {
                result.add(rs.getTimestamp(1).toInstant());
            }
            return result;
        }
    }

    private static SchedulingTelemetry telemetry() {
        var telemetry = Mockito.mock(SchedulingTelemetry.class);
        when(telemetry.observe()).thenReturn(NoopSchedulingObservation.INSTANCE);
        doReturn(DbSchedulerJobConfigurationTest.class).when(telemetry).jobClass();
        when(telemetry.jobMethod()).thenReturn("job");
        return telemetry;
    }
}
