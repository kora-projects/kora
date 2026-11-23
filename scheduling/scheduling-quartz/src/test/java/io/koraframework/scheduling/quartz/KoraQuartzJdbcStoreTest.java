package io.koraframework.scheduling.quartz;

import io.koraframework.application.graph.ValueOf;
import io.koraframework.scheduling.common.telemetry.SchedulingTelemetry;
import io.koraframework.scheduling.common.telemetry.impl.NoopSchedulingObservation;
import io.koraframework.test.postgres.PostgresParams;
import io.koraframework.test.postgres.PostgresTestContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobExecutionContext;
import org.quartz.JobKey;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.TriggerKey;
import io.koraframework.scheduling.quartz.util.QuartzCronUtils;
import org.quartz.utils.ConnectionProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(PostgresTestContainer.class)
class KoraQuartzJdbcStoreTest {

    private static final String TRIGGER = "kora-test-trigger";
    private static final AtomicInteger RUNS = new AtomicInteger();

    private PostgresParams params;

    @BeforeEach
    void setUp(PostgresParams params) throws IOException {
        this.params = params;
        try (var is = org.quartz.Scheduler.class.getClassLoader().getResourceAsStream("org/quartz/impl/jdbcjobstore/tables_postgres.sql")) {
            assertThat(is).isNotNull();
            params.execute(new String(is.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    void orphanedJobsOfRemovedClassesAreRemovedBeforeStart() throws Exception {
        var scheduler = scheduler(config(true, false), List.of());
        scheduler.init();
        try {
            addJob(scheduler, JobKey.jobKey("com.example.$Removed_job_Job", KoraQuartzJobRegistrar.JOB_GROUP));
            addJob(scheduler, JobKey.jobKey("com.example.$Legacy_job_Job"));
            addJob(scheduler, JobKey.jobKey("manual-job"));
        } finally {
            scheduler.release();
        }
        // the classes of these jobs no longer exist
        this.params.execute("UPDATE qrtz_job_details SET job_class_name = 'com.example.RemovedJob' WHERE job_name <> 'manual-job'");

        var restarted = scheduler(config(true, false), List.of());
        restarted.init();
        try {
            assertThat(restarted.value().checkExists(JobKey.jobKey("com.example.$Removed_job_Job", KoraQuartzJobRegistrar.JOB_GROUP))).isFalse();
            assertThat(restarted.value().checkExists(JobKey.jobKey("com.example.$Legacy_job_Job"))).isFalse();
            assertThat(restarted.value().checkExists(JobKey.jobKey("manual-job"))).isTrue();
            // the scheduler is started by KoraQuartzJobRegistrar once the jobs are registered
            assertThat(restarted.value().isInStandbyMode()).isTrue();
        } finally {
            restarted.release();
        }
    }

    @Test
    void orphanedJobsAreKeptByDefault() throws Exception {
        var scheduler = scheduler(new QuartzConfig() {}, List.of());
        scheduler.init();
        try {
            addJob(scheduler, JobKey.jobKey("com.example.$Removed_job_Job", KoraQuartzJobRegistrar.JOB_GROUP));
        } finally {
            scheduler.release();
        }

        var restarted = scheduler(new QuartzConfig() {}, List.of());
        restarted.init();
        try {
            assertThat(restarted.value().checkExists(JobKey.jobKey("com.example.$Removed_job_Job", KoraQuartzJobRegistrar.JOB_GROUP))).isTrue();
        } finally {
            restarted.release();
        }
    }

    @Test
    void knownJobIsKeptByCleanup() throws Exception {
        var job = job(cronTrigger(null));
        start(config(true, false), job).release();

        var restarted = scheduler(config(true, false), List.of(job));
        restarted.init();
        try {
            assertThat(restarted.value().checkExists(KoraQuartzJobRegistrar.jobKey(TestJob.class))).isTrue();
        } finally {
            restarted.release();
        }
    }

    @Test
    void legacyJobOfKnownClassIsMovedToKoraGroup() throws Exception {
        var legacyKey = JobKey.jobKey(KoraQuartzJobRegistrar.jobName(TestJob.class));
        var scheduler = scheduler(config(true, false), List.of());
        scheduler.init();
        try {
            var legacyJob = JobBuilder.newJob(TestJob.class).withIdentity(legacyKey).storeDurably().build();
            scheduler.value().addJob(legacyJob, true);
            scheduler.value().scheduleJob(cronTrigger(null).getTriggerBuilder().forJob(legacyJob).build());
        } finally {
            scheduler.release();
        }

        var registered = start(config(true, false), job(cronTrigger(null)));
        try {
            var quartz = registered.value();
            assertThat(quartz.checkExists(legacyKey)).isFalse();
            assertThat(quartz.getTrigger(TriggerKey.triggerKey(TRIGGER)).getJobKey()).isEqualTo(KoraQuartzJobRegistrar.jobKey(TestJob.class));
        } finally {
            registered.release();
        }
    }

    @Test
    void restartKeepsPersistedTriggerByDefault() throws Exception {
        var persisted = persistAndRestart(config(true, false), cronTrigger(null), cronTrigger(null));

        assertThat(persisted.after().getStartTime()).isEqualTo(persisted.before().getStartTime());
        assertThat(persisted.after().getNextFireTime()).isEqualTo(persisted.before().getNextFireTime());
    }

    @Test
    void restartKeepsPhaseOfPersistedSimpleTriggerByDefault() throws Exception {
        var persisted = persistAndRestart(config(true, false), simpleTrigger(), simpleTrigger());

        // the next fire time is not compared: a trigger starting now may fire right after registration
        assertThat(persisted.after().getStartTime()).isEqualTo(persisted.before().getStartTime());
    }

    @Test
    void restartReschedulesTriggerWithChangedStartTimeWhenComparisonIsEnabled() throws Exception {
        var persisted = persistAndRestart(config(true, true), cronTrigger(null), cronTrigger(null));

        assertThat(persisted.after().getStartTime()).isAfter(persisted.before().getStartTime());
    }

    @Test
    void restartReschedulesTriggerWithChangedEndTime() throws Exception {
        var endTime = Date.from(Instant.now().plus(Duration.ofDays(10)));
        var persisted = persistAndRestart(config(true, false), cronTrigger(null), cronTrigger(endTime));

        assertThat(persisted.before().getEndTime()).isNull();
        assertThat(persisted.after().getEndTime()).isEqualTo(endTime);
    }

    @Test
    void restartReschedulesTriggerWithChangedCron() throws Exception {
        var changed = TriggerBuilder.newTrigger()
            .withIdentity(TRIGGER)
            .withSchedule(CronScheduleBuilder.cronSchedule("0 30 12 * * ?"))
            .build();
        var persisted = persistAndRestart(config(true, false), cronTrigger(null), changed);

        assertThat(((org.quartz.CronTrigger) persisted.after()).getCronExpression()).isEqualTo("0 30 12 * * ?");
    }

    @Test
    void restartReschedulesTriggerWithChangedTimeZone() throws Exception {
        var zone = ZoneId.of("Pacific/Kiritimati");
        var changed = TriggerBuilder.newTrigger()
            .withIdentity(TRIGGER)
            .withSchedule(QuartzCronUtils.cronSchedule("0 0 12 * * ?", zone, KoraQuartzJdbcStoreTest.class, "job"))
            .build();
        var persisted = persistAndRestart(config(true, false), cronTrigger(null), changed);

        assertThat(((org.quartz.CronTrigger) persisted.after()).getTimeZone()).isEqualTo(TimeZone.getTimeZone(zone));
    }

    @Test
    void jobWithoutTriggersRemovesPersistedTriggers() throws Exception {
        start(config(true, false), job(cronTrigger(null))).release();

        var disabled = start(config(true, false), new TestJob(telemetry(), List.of()));
        try {
            assertThat(disabled.value().checkExists(TriggerKey.triggerKey(TRIGGER))).isFalse();
            assertThat(disabled.value().checkExists(KoraQuartzJobRegistrar.jobKey(TestJob.class))).isTrue();
        } finally {
            disabled.release();
        }
    }

    @Test
    void disabledJobDoesNotFireDuePersistedTriggerOnRestart() throws Exception {
        startEverySecondAndStop();

        var disabled = start(config(true, false), new TestJob(telemetry(), List.of()));
        try {
            Thread.sleep(2000);
        } finally {
            disabled.release();
        }
        assertThat(RUNS).hasValue(0);
    }

    @Test
    void changedCronDoesNotFireDuePersistedTriggerOnRestart() throws Exception {
        startEverySecondAndStop();

        var changed = start(config(true, false), job(TriggerBuilder.newTrigger()
            .withIdentity(TRIGGER)
            .withSchedule(CronScheduleBuilder.cronSchedule("0 0 12 1 1 ?"))
            .build()));
        try {
            Thread.sleep(2000);
        } finally {
            changed.release();
        }
        assertThat(RUNS).hasValue(0);
    }

    /**
     * Runs a job firing every second, then keeps the application down until the persisted trigger is due.
     */
    private void startEverySecondAndStop() throws Exception {
        var scheduler = start(config(true, false), job(TriggerBuilder.newTrigger()
            .withIdentity(TRIGGER)
            .withSchedule(CronScheduleBuilder.cronSchedule("0/1 * * * * ?"))
            .build()));
        Thread.sleep(1500);
        scheduler.release();
        Thread.sleep(2500);
        RUNS.set(0);
    }

    private record Persisted(Trigger before, Trigger after) {}

    private Persisted persistAndRestart(QuartzConfig config, Trigger first, Trigger second) throws Exception {
        Trigger before;
        var scheduler = start(config, job(first));
        try {
            before = scheduler.value().getTrigger(TriggerKey.triggerKey(TRIGGER));
        } finally {
            scheduler.release();
        }
        // a restart builds the triggers again, so a trigger without startAt() gets a new start time
        Thread.sleep(1100);
        var restarted = start(config, job(rebuild(second)));
        try {
            return new Persisted(before, restarted.value().getTrigger(TriggerKey.triggerKey(TRIGGER)));
        } finally {
            restarted.release();
        }
    }

    private static Trigger rebuild(Trigger trigger) {
        var builder = trigger.getTriggerBuilder().startNow();
        if (trigger.getStartTime() != null && trigger.getStartTime().after(new Date())) {
            builder.startAt(trigger.getStartTime());
        }
        return builder.build();
    }

    private KoraQuartzScheduler start(QuartzConfig config, TestJob job) {
        var scheduler = scheduler(config, List.of(job));
        try {
            scheduler.init();
            new KoraQuartzJobRegistrar(List.of(valueOf(job)), scheduler.value(), config).init();
            return scheduler;
        } catch (Exception e) {
            scheduler.release();
            throw new IllegalStateException(e);
        }
    }

    private KoraQuartzScheduler scheduler(QuartzConfig config, List<TestJob> jobs) {
        var properties = new Properties();
        properties.setProperty("org.quartz.scheduler.instanceName", "kora-jdbc-test");
        properties.setProperty("org.quartz.threadPool.threadCount", "1");
        properties.setProperty("org.quartz.jobStore.class", "org.quartz.impl.jdbcjobstore.JobStoreTX");
        properties.setProperty("org.quartz.jobStore.driverDelegateClass", "org.quartz.impl.jdbcjobstore.PostgreSQLDelegate");
        properties.setProperty("org.quartz.jobStore.dataSource", "test");
        properties.setProperty("org.quartz.dataSource.test.connectionProvider.class", TestConnectionProvider.class.getName());
        properties.setProperty("org.quartz.dataSource.test.url", this.params.jdbcUrl());
        properties.setProperty("org.quartz.dataSource.test.user", this.params.user());
        properties.setProperty("org.quartz.dataSource.test.password", this.params.password());
        var values = jobs.stream().map(KoraQuartzJdbcStoreTest::valueOf).toList();
        return new KoraQuartzScheduler(new KoraQuartzJobFactory(values), properties, config);
    }

    private static void addJob(KoraQuartzScheduler scheduler, JobKey key) throws Exception {
        var job = JobBuilder.newJob(TestJob.class).withIdentity(key).storeDurably().build();
        scheduler.value().addJob(job, true);
        scheduler.value().scheduleJob(TriggerBuilder.newTrigger()
            .withIdentity(key.getName() + "-trigger")
            .forJob(job)
            .withSchedule(CronScheduleBuilder.cronSchedule("0 0 12 * * ?"))
            .build());
    }

    private static QuartzConfig config(boolean cleanupOrphanedJobs, boolean compareStartTime) {
        return new QuartzConfig() {
            @Override
            public boolean cleanupOrphanedJobs() {
                return cleanupOrphanedJobs;
            }

            @Override
            public boolean compareStartTime() {
                return compareStartTime;
            }
        };
    }

    private static Trigger cronTrigger(Date endTime) {
        return TriggerBuilder.newTrigger()
            .withIdentity(TRIGGER)
            .withSchedule(CronScheduleBuilder.cronSchedule("0 0 12 * * ?"))
            .endAt(endTime)
            .build();
    }

    private static Trigger simpleTrigger() {
        return TriggerBuilder.newTrigger()
            .withIdentity(TRIGGER)
            .withSchedule(SimpleScheduleBuilder.repeatHourlyForever())
            .build();
    }

    private static TestJob job(Trigger trigger) {
        return new TestJob(telemetry(), List.of(trigger));
    }

    private static SchedulingTelemetry telemetry() {
        var telemetry = Mockito.mock(SchedulingTelemetry.class);
        when(telemetry.observe()).thenReturn(NoopSchedulingObservation.INSTANCE);
        return telemetry;
    }

    private static ValueOf<KoraQuartzJob> valueOf(KoraQuartzJob job) {
        return () -> job;
    }

    public static final class TestJob extends KoraQuartzJob {
        public TestJob(SchedulingTelemetry telemetry, List<Trigger> triggers) {
            super(telemetry, (JobExecutionContext context) -> RUNS.incrementAndGet(), triggers);
        }
    }

    public static final class TestConnectionProvider implements ConnectionProvider {
        private String url;
        private String user;
        private String password;

        public void setUrl(String url) {
            this.url = url;
        }

        public void setUser(String user) {
            this.user = user;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return DriverManager.getConnection(this.url, this.user, this.password);
        }

        @Override
        public void shutdown() {
        }

        @Override
        public void initialize() {
        }
    }
}
