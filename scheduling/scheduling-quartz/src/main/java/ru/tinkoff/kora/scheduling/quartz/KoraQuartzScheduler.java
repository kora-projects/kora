package ru.tinkoff.kora.scheduling.quartz;

import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.impl.matchers.GroupMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.common.util.TimeUtils;

import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

public class KoraQuartzScheduler implements Wrapped<Scheduler>, Lifecycle {

    private static final Logger logger = LoggerFactory.getLogger(KoraQuartzScheduler.class);

    private final KoraQuartzJobFactory jobFactory;
    private final Properties properties;
    private final SchedulingQuartzConfig config;

    private volatile Scheduler scheduler = null;

    public KoraQuartzScheduler(KoraQuartzJobFactory jobFactory,
                               Properties properties,
                               SchedulingQuartzConfig config) {
        this.jobFactory = jobFactory;
        this.properties = properties;
        this.config = config;
    }

    @Override
    public void init() throws SchedulerException {
        logger.debug("KoraQuartzScheduler starting...");
        var started = System.nanoTime();

        var propertiesToUse = new Properties();
        for (var property : this.properties.stringPropertyNames()) {
            propertiesToUse.setProperty(property, this.properties.getProperty(property));
        }

        // TODO real scheduler
        var factory = new StdSchedulerFactory();
        factory.initialize(propertiesToUse);
        this.scheduler = factory.getScheduler();
        this.scheduler.setJobFactory(this.jobFactory);
        if (this.config.cleanupOrphanedJobs()) {
            cleanupOrphanedJobs(this.scheduler, this.jobFactory.jobClasses());
        }
        this.scheduler.start();
        this.scheduler.checkExists(JobKey.jobKey("_that_job_should_not_exist"));

        logger.info("KoraQuartzScheduler started in {}", TimeUtils.tookForLogging(started));
    }

    static void cleanupOrphanedJobs(Scheduler scheduler, Set<Class<?>> knownJobClasses) throws SchedulerException {
        var knownKeys = knownJobClasses.stream()
            .map(jobClass -> JobKey.jobKey(jobClass.getCanonicalName()))
            .collect(Collectors.toSet());

        var removed = new HashSet<String>();
        for (var jobKey : scheduler.getJobKeys(GroupMatcher.anyJobGroup())) {
            if (!knownKeys.contains(jobKey)) {
                if (scheduler.deleteJob(jobKey)) {
                    removed.add(jobKey.toString());
                }
            }
        }

        if (!removed.isEmpty()) {
            logger.info("Removed {} orphaned Quartz job(s) no longer present in the application graph", removed);
        }
    }

    @Override
    public void release() {
        if (this.scheduler != null) {
            logger.debug("KoraQuartzScheduler stopping...");
            var started = System.nanoTime();

            try {
                final boolean waitForComplete = config.waitForJobComplete();
                if (waitForComplete) {
                    logger.debug("KoraQuartzScheduler awaiting graceful shutdown...");
                }
                scheduler.shutdown(waitForComplete);
            } catch (SchedulerException e) {
                logger.warn("KoraQuartzScheduler failed completing graceful shutdown", e);
            }

            logger.info("KoraQuartzScheduler stopped in {}", TimeUtils.tookForLogging(started));
            this.scheduler = null;
        }
    }

    @Override
    public Scheduler value() {
        return this.scheduler;
    }
}
