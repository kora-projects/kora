package io.koraframework.scheduling.db;

import com.github.kagkarlsson.scheduler.SchedulerBuilder;
import io.koraframework.application.graph.All;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.common.Configurer;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.Tag;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.scheduling.common.SchedulingModule;
import io.koraframework.scheduling.db.job.SchedulingDbJob;
import org.jspecify.annotations.Nullable;

import javax.sql.DataSource;

public interface SchedulingDbModule extends SchedulingModule {

    default SchedulingDbConfig schedulingDbConfig(Config config, ConfigValueMapper<SchedulingDbConfig> mapper) {
        return mapper.mapOrThrow(config.get("scheduling.db"));
    }

    @Tag(SchedulingDbScheduler.class)
    @DefaultComponent
    default DataSource schedulingDbDataSource(DataSource dataSource) {
        return dataSource;
    }

    @DefaultComponent
    default SchedulingDbScheduler schedulingDbScheduler(@Tag(SchedulingDbScheduler.class) DataSource dataSource,
                                                        SchedulingDbConfig config,
                                                        All<ValueOf<SchedulingDbJob>> jobs,
                                                        @Nullable Configurer<SchedulerBuilder> schedulerBuilderConfigurer) {
        return new SchedulingDbScheduler(dataSource, config, jobs, schedulerBuilderConfigurer);
    }
}
