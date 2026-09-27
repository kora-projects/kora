package ru.tinkoff.kora.scheduling.quartz;

import org.junit.jupiter.api.Test;
import org.quartz.Job;
import org.quartz.JobBuilder;
import org.quartz.JobExecutionContext;
import org.quartz.Scheduler;
import org.quartz.impl.StdSchedulerFactory;

import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class KoraQuartzSchedulerTest {

    @Test
    void cleanupRemovesOrphanedJobsButKeepsKnownOnes() throws Exception {
        java.util.logging.Logger.getLogger("org.quartz").setLevel(java.util.logging.Level.OFF);

        var properties = new Properties();
        properties.setProperty("org.quartz.threadPool.threadCount", "1");
        var factory = new StdSchedulerFactory();
        factory.initialize(properties);
        var scheduler = factory.getScheduler();
        try {
            var knownJob = JobBuilder.newJob(TestJob.class)
                .withIdentity(TestJob.class.getCanonicalName())
                .storeDurably()
                .build();
            var orphanedJob = JobBuilder.newJob(TestJob.class)
                .withIdentity("com.example.DeletedScheduler_deletedCron_Job")
                .storeDurably()
                .build();
            scheduler.addJob(knownJob, true);
            scheduler.addJob(orphanedJob, true);

            KoraQuartzScheduler.cleanupOrphanedJobs(scheduler, Set.of(TestJob.class));

            assertThat(scheduler.checkExists(knownJob.getKey())).isTrue();
            assertThat(scheduler.checkExists(orphanedJob.getKey())).isFalse();
        } finally {
            scheduler.shutdown();
        }
    }

    @Test
    void cleanupKeepsAllJobsWhenAllAreKnown() throws Exception {
        java.util.logging.Logger.getLogger("org.quartz").setLevel(java.util.logging.Level.OFF);

        var properties = new Properties();
        properties.setProperty("org.quartz.threadPool.threadCount", "1");
        var factory = new StdSchedulerFactory();
        factory.initialize(properties);
        var scheduler = factory.getScheduler();
        try {
            var knownJob = JobBuilder.newJob(TestJob.class)
                .withIdentity(TestJob.class.getCanonicalName())
                .storeDurably()
                .build();
            scheduler.addJob(knownJob, true);

            KoraQuartzScheduler.cleanupOrphanedJobs(scheduler, Set.of(TestJob.class));

            assertThat(scheduler.checkExists(knownJob.getKey())).isTrue();
        } finally {
            scheduler.shutdown();
        }
    }

    public static class TestJob implements Job {
        @Override
        public void execute(JobExecutionContext context) {
        }
    }
}