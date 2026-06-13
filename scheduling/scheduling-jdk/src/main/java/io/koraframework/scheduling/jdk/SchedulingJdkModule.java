package io.koraframework.scheduling.jdk;

import io.koraframework.application.graph.All;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.scheduling.common.SchedulingJobConfig;
import io.koraframework.scheduling.common.SchedulingJobConfig;
import io.koraframework.scheduling.common.SchedulingModule;

public interface SchedulingJdkModule extends SchedulingModule {

    default SchedulingJdkConfig schedulingJdkConfig(Config config, ConfigValueMapper<SchedulingJdkConfig> mapper) {
        return mapper.mapOrThrow(config.get("scheduling.jdk"));
    }

    @DefaultComponent
    default SchedulingJdkExecutor defaultSchedulingJdkExecutor(All<ValueOf<SchedulingJobConfig>> jobConfigs,
                                                                    SchedulingJdkConfig config) {
        return new ThreadPoolSchedulingJdkExecutor(jobConfigs, config);
    }
}
