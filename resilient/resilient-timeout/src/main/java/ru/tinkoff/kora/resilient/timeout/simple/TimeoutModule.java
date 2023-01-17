package ru.tinkoff.kora.resilient.timeout.simple;

import com.typesafe.config.Config;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.config.common.extractor.ObjectConfigValueExtractor;
import ru.tinkoff.kora.resilient.timeout.TimeouterManager;
import ru.tinkoff.kora.resilient.timeout.annotation.Timeout;
import ru.tinkoff.kora.resilient.timeout.telemetry.TimeoutMetrics;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public interface TimeoutModule {

    default ConfigValueExtractor<SimpleTimeoutConfig> simpleTimeoutConfigValueExtractor(ConfigValueExtractor<Map<String, SimpleTimeoutConfig.NamedConfig>> extractor) {
        return new ObjectConfigValueExtractor<>() {
            @Override
            protected SimpleTimeoutConfig extract(Config config) {
                var fast = Map.<String, SimpleTimeoutConfig.NamedConfig>of();
                if (config.hasPath("timeout")) {
                    fast = extractor.extract(config.getValue("timeout"));
                }
                return new SimpleTimeoutConfig(fast);
            }
        };
    }

    default SimpleTimeoutConfig simpleTimeoutConfig(Config config, ConfigValueExtractor<SimpleTimeoutConfig> extractor) {
        return !config.hasPath("resilient")
            ? new SimpleTimeoutConfig(Map.of())
            : extractor.extract(config.getValue("resilient"));
    }

    default TimeouterManager simpleTimeoutManager(@Tag(Timeout.class) ExecutorService executorService,
                                                  SimpleTimeoutConfig config,
                                                  @Nullable TimeoutMetrics metrics) {
        return new SimpleTimeouterManager(metrics == null
            ? new NoopTimeoutMetrics()
            : metrics,
            executorService, config);
    }

    @DefaultComponent
    @Tag(Timeout.class)
    default ExecutorService simpleTimeoutExecutorService() {
        return Executors.newCachedThreadPool();
    }
}
