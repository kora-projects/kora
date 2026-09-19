package io.koraframework.scheduling.jdk;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.scheduling.common.SchedulingModule;

public interface SchedulingJdkModule extends SchedulingModule {

    default SchedulingJdkConfig schedulingJdkConfig(Config config, ConfigValueMapper<SchedulingJdkConfig> mapper) {
        return mapper.mapOrThrow(config.get("scheduling.jdk"));
    }

    // Jobs depend on the executor, so the executor must not depend on jobs: a cycle through All<T>
    // can not be broken by a promised proxy and fails the application graph.
    @DefaultComponent
    default SchedulingJdkExecutor defaultSchedulingJdkExecutor(SchedulingJdkConfig config) {
        return new VirtualThreadSchedulingJdkExecutor(config);
    }
}
