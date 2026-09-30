package io.koraframework.scheduling.db.scheduler;

import com.github.kagkarlsson.scheduler.SchedulerBuilder;
import io.koraframework.application.graph.All;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.common.Configurer;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.Root;
import io.koraframework.common.annotation.Tag;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.scheduling.common.SchedulingModule;
import io.koraframework.scheduling.db.scheduler.job.DbSchedulerJob;
import org.jspecify.annotations.Nullable;

import javax.sql.DataSource;

public interface DbSchedulerModule extends SchedulingModule {

    default DbSchedulerConfig dbSchedulerConfig(Config config, ConfigValueMapper<DbSchedulerConfig> mapper) {
        return mapper.mapOrThrow(config.get("scheduling.dbScheduler"));
    }

    @Tag(KoraDbScheduler.class)
    @DefaultComponent
    default DataSource dbSchedulerDataSource(DataSource dataSource) {
        return dataSource;
    }

    @Root
    @DefaultComponent
    default KoraDbScheduler koraDbScheduler(@Tag(KoraDbScheduler.class) DataSource dataSource,
                                            DbSchedulerConfig config,
                                            All<ValueOf<DbSchedulerJob>> jobs,
                                            @Nullable Configurer<SchedulerBuilder> schedulerBuilderConfigurer) {
        return new KoraDbScheduler(dataSource, config, jobs, schedulerBuilderConfigurer);
    }
}
