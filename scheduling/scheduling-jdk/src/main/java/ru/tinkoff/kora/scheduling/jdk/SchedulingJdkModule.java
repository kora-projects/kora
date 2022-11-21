package ru.tinkoff.kora.scheduling.jdk;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigValueFactory;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.scheduling.common.SchedulingModule;

import java.util.Map;

public interface SchedulingJdkModule extends SchedulingModule {
    default ScheduledExecutorServiceConfig scheduledExecutorServiceConfig(Config config, ConfigValueExtractor<ScheduledExecutorServiceConfig> extractor) {
        if (config.hasPath("scheduling")) {
            return extractor.extract(config.getValue("scheduling"));
        } else {
            return extractor.extract(ConfigValueFactory.fromMap(Map.of()));
        }
    }

    default ScheduledExecutorServiceLifecycle scheduledExecutorServiceLifecycle(ScheduledExecutorServiceConfig config) {
        return new ScheduledExecutorServiceLifecycle(config);
    }
}
