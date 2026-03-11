package io.koraframework.scheduling.jdk;

import io.koraframework.common.DefaultComponent;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractor;
import io.koraframework.scheduling.common.SchedulingModule;

public interface SchedulingJdkModule extends SchedulingModule {

    default ScheduledExecutorServiceConfig scheduledExecutorServiceConfig(Config config, ConfigValueExtractor<ScheduledExecutorServiceConfig> extractor) {
        var value = config.get("scheduling");
        return extractor.extract(value);
    }

    @DefaultComponent
    default JdkSchedulingExecutor scheduledExecutorServiceLifecycle(ScheduledExecutorServiceConfig config) {
        return new DefaultJdkSchedulingExecutor(config);
    }
}
