package ru.tinkoff.kora.scheduling.quartz;

import com.typesafe.config.Config;
import org.quartz.Scheduler;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.scheduling.common.SchedulingModule;

import java.util.Properties;

public interface QuartzModule extends SchedulingModule {
    @Tag(QuartzModule.class)
    default Properties quartzProperties(Config config, ConfigValueExtractor<Properties> extractor) {
        var value = config.getValue("quartz");
        return extractor.extract(value);
    }

    default KoraQuartzJobFactory koraQuartzJobFactory(All<KoraQuartzJob> jobs) {
        return new KoraQuartzJobFactory(jobs);
    }

    default KoraQuartzScheduler koraQuartzScheduler(KoraQuartzJobFactory jobFactory, @Tag(QuartzModule.class) Properties properties) {
        return new KoraQuartzScheduler(jobFactory, properties);
    }

    default KoraQuartzJobRegistrar koraQuartzJobRegistrar(All<KoraQuartzJob> jobs, Scheduler scheduler) {
        return new KoraQuartzJobRegistrar(jobs, scheduler);
    }
}
