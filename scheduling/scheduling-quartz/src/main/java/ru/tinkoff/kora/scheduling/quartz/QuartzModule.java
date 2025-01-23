package ru.tinkoff.kora.scheduling.quartz;

import org.quartz.Scheduler;
import org.quartz.impl.StdSchedulerFactory;
import ru.tinkoff.kora.application.graph.All;
import ru.tinkoff.kora.application.graph.ValueOf;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.common.annotation.Root;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.scheduling.common.SchedulingModule;

import java.io.IOException;
import java.util.Properties;

public interface QuartzModule extends SchedulingModule {

    @Tag(QuartzModule.class)
    default Properties quartzProperties(Config config, ConfigValueExtractor<Properties> extractor) throws IOException {
        var value = config.get("quartz");
        var defaults = new Properties();
        try (var is = Thread.currentThread().getContextClassLoader().getResourceAsStream("org/quartz/quartz.properties")) {
            if (is != null) {
                defaults.load(is);
            }
        }
        defaults.setProperty(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, "kora-quartz-scheduler");
        defaults.setProperty(StdSchedulerFactory.PROP_SCHED_INSTANCE_ID, StdSchedulerFactory.AUTO_GENERATE_INSTANCE_ID);

        var props = extractor.extract(value);
        if (props == null) {
            return defaults;
        } else {
            for (var entry : defaults.entrySet()) {
                if (!props.containsKey(entry.getKey())) {
                    props.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return props;
    }

    default SchedulingQuartzConfig schedulingQuartzConfig(Config config, ConfigValueExtractor<SchedulingQuartzConfig> extractor) {
        var value = config.get("scheduling");
        return extractor.extract(value);
    }

    default KoraQuartzJobFactory koraQuartzJobFactory(All<ValueOf<KoraQuartzJob>> jobs) {
        return new KoraQuartzJobFactory(jobs);
    }

    @Root
    default KoraQuartzScheduler koraQuartzScheduler(KoraQuartzJobFactory jobFactory,
                                                    @Tag(QuartzModule.class) Properties properties,
                                                    SchedulingQuartzConfig config) {
        return new KoraQuartzScheduler(jobFactory, properties, config);
    }

    @Root
    default KoraQuartzJobRegistrar koraQuartzJobRegistrar(All<ValueOf<KoraQuartzJob>> jobs, Scheduler scheduler) {
        return new KoraQuartzJobRegistrar(jobs, scheduler);
    }
}
