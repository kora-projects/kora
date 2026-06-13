package io.koraframework.scheduling.db;

import io.koraframework.application.graph.All;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.common.DefaultComponent;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractor;
import io.koraframework.scheduling.common.SchedulingModule;

import javax.sql.DataSource;

public interface SchedulingDbModule extends SchedulingModule {

    default SchedulingDbConfig schedulingDbConfig(Config config, ConfigValueExtractor<SchedulingDbConfig> extractor) {
        return extractor.extract(config.get("scheduling.db"));
    }

    @DefaultComponent
    default DbSchedulerLifecycle dbSchedulerLifecycle(DataSource dataSource,
                                                      SchedulingDbConfig config,
                                                      All<ValueOf<DbScheduledJob>> jobs) {
        return new DbSchedulerLifecycle(dataSource, config, jobs);
    }
}
