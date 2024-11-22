package ru.tinkoff.kora.scheduling.quartz;

import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.common.util.TimeUtils;

import java.util.Properties;

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

        // TODO real scheduler
        var factory = new StdSchedulerFactory();
        factory.initialize(this.properties);
        this.scheduler = factory.getScheduler();
        this.scheduler.setJobFactory(this.jobFactory);
        this.scheduler.start();
        this.scheduler.checkExists(JobKey.jobKey("_that_job_should_not_exist"));

        logger.info("KoraQuartzScheduler started in {}", TimeUtils.tookForLogging(started));
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
