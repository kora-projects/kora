package ru.tinkoff.kora.scheduling.quartz;

import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import ru.tinkoff.kora.application.graph.Lifecycle;
import ru.tinkoff.kora.application.graph.Wrapped;
import ru.tinkoff.kora.common.util.ReactorUtils;

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
    public Mono<?> init() {
        return ReactorUtils.ioMono(() -> {
            try {
                // TODO real scheduler
                var factory = new StdSchedulerFactory();
                factory.initialize(this.properties);
                this.scheduler = factory.getScheduler();
                this.scheduler.setJobFactory(this.jobFactory);
                this.scheduler.start();
                this.scheduler.checkExists(JobKey.jobKey("_that_job_should_not_exist"));
            } catch (SchedulerException e) {
                throw Exceptions.propagate(e);
            }
        });
    }

    @Override
    public Mono<?> release() {
        return ReactorUtils.ioMono(() -> {
            if (this.scheduler != null) {
                try {
                    this.scheduler.shutdown(true);
                } catch (SchedulerException e) {
                    throw Exceptions.propagate(e);
                }
            }
        });
    }


    @Override
    public Scheduler value() {
        return this.scheduler;
    }
}
