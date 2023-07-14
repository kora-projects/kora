package ru.tinkoff.kora.scheduling.quartz;

import org.quartz.Scheduler;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.scheduling.common.SchedulingModule;

import java.util.Properties;

public interface QuartzModule extends SchedulingModule {
    @Tag(QuartzModule.class)
    default Properties quartzProperties(Config config, ConfigValueExtractor<Properties> extractor) {
        var value = config.get("quartz");
        return extractor.extract(value);
    }

    default KoraQuartzJobFactory koraQuartzJobFactory(All<KoraQuartzJob> jobs) {
        return new KoraQuartzJobFactory(jobs);
    }

    @Root
    default KoraQuartzScheduler koraQuartzScheduler(KoraQuartzJobFactory jobFactory, @Tag(QuartzModule.class) Properties properties) {
        return new KoraQuartzScheduler(jobFactory, properties);
    }

    @Root
    default KoraQuartzJobRegistrar koraQuartzJobRegistrar(All<KoraQuartzJob> jobs, Scheduler scheduler) {
        return new KoraQuartzJobRegistrar(jobs, scheduler);
    }
}
