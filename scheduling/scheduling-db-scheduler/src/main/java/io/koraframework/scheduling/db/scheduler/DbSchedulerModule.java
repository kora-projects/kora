package io.koraframework.scheduling.db.scheduler;

import com.github.kagkarlsson.scheduler.SchedulerBuilder;
import io.koraframework.application.graph.All;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.common.Configurer;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.Tag;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.scheduling.common.SchedulingModule;
import io.koraframework.scheduling.db.scheduler.job.DbSchedulerJob;
import org.jspecify.annotations.Nullable;

import javax.sql.DataSource;

public interface DbSchedulerModule extends SchedulingModule {

    default DbSchedulerConfig schedulingDbConfig(Config config, ConfigValueMapper<DbSchedulerConfig> mapper) {
        return mapper.mapOrThrow(config.get("scheduling.dbScheduler"));
    }

    @Tag(DbSchedulerWrapper.class)
    @DefaultComponent
    default DataSource schedulingDbDataSource(DataSource dataSource) {
        return dataSource;
    }

    @DefaultComponent
    default DbSchedulerWrapper schedulingDbScheduler(@Tag(DbSchedulerWrapper.class) DataSource dataSource,
                                                     DbSchedulerConfig config,
                                                     All<ValueOf<DbSchedulerJob>> jobs,
                                                     @Nullable Configurer<SchedulerBuilder> schedulerBuilderConfigurer) {
        return new DbSchedulerWrapper(dataSource, config, jobs, schedulerBuilderConfigurer);
    }
}
