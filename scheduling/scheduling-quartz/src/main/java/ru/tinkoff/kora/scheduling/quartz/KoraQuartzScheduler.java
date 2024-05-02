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
    private volatile Scheduler scheduler = null;

    public KoraQuartzScheduler(KoraQuartzJobFactory jobFactory, Properties properties) {
        this.jobFactory = jobFactory;
        this.properties = properties;
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
    public void release() throws SchedulerException {
        if (this.scheduler != null) {
            logger.debug("KoraQuartzScheduler stopping...");
            var started = System.nanoTime();

            this.scheduler.shutdown(true);

            logger.info("KoraQuartzScheduler stopped in {}", TimeUtils.tookForLogging(started));
        }
    }


    @Override
    public Scheduler value() {
        return this.scheduler;
    }
}
