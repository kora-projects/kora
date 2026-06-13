package io.koraframework.scheduling.jdk;

import io.koraframework.application.graph.All;
import io.koraframework.application.graph.ValueOf;
import io.koraframework.common.DefaultComponent;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractor;
import io.koraframework.scheduling.common.SchedulingJobConfig;
import io.koraframework.scheduling.common.SchedulingModule;

public interface SchedulingJdkModule extends SchedulingModule {

    default SchedulingJdkConfig schedulingJdkConfig(Config config, ConfigValueExtractor<SchedulingJdkConfig> extractor) {
        var value = config.get("scheduling.jdk");
        return extractor.extract(value);
    }

    @DefaultComponent
    default SchedulingJdkExecutor defaultSchedulingJdkExecutor(All<ValueOf<SchedulingJobConfig>> jobConfigs,
                                                                    SchedulingJdkConfig config) {
        return new ThreadPoolSchedulingJdkExecutor(jobConfigs, config);
    }
}
