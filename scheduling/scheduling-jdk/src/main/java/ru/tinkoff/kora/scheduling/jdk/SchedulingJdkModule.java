package ru.tinkoff.kora.scheduling.jdk;

import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.config.common.factory.MapConfigFactory;
import ru.tinkoff.kora.scheduling.common.SchedulingModule;

import java.util.Map;

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
