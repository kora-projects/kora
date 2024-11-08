package ru.tinkoff.kora.scheduling.jdk;

import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.scheduling.common.SchedulingModule;

public interface SchedulingJdkModule extends SchedulingModule {

    default SchedulingJdkConfig scheduledExecutorServiceConfig(Config config, ConfigValueExtractor<SchedulingJdkConfig> extractor) {
        var value = config.get("scheduling");
        return extractor.extract(value);
    }

    @DefaultComponent
    default JdkSchedulingExecutor scheduledExecutorServiceLifecycle(SchedulingJdkConfig config) {
        return new DefaultJdkSchedulingExecutor(config);
    }
}
