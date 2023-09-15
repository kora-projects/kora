package ru.tinkoff.kora.scheduling.quartz;

import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Wrapped;

import java.util.Properties;

public class KoraQuartzScheduler implements Wrapped<Scheduler>, Lifecycle {
    private final KoraQuartzJobFactory jobFactory;
    private final Properties properties;
    private volatile Scheduler scheduler = null;

    public KoraQuartzScheduler(KoraQuartzJobFactory jobFactory, Properties properties) {
        this.jobFactory = jobFactory;
        this.properties = properties;
    }

    @Override
    public void init() throws SchedulerException {
        // TODO real scheduler
        var factory = new StdSchedulerFactory();
        factory.initialize(this.properties);
        this.scheduler = factory.getScheduler();
        this.scheduler.setJobFactory(this.jobFactory);
        this.scheduler.start();
        this.scheduler.checkExists(JobKey.jobKey("_that_job_should_not_exist"));
    }

    @Override
    public void release() throws SchedulerException {
        if (this.scheduler != null) {
            this.scheduler.shutdown(true);
        }
    }


    @Override
    public Scheduler value() {
        return this.scheduler;
    }
}
