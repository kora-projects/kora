package ru.tinkoff.kora.resilient.timeout.simple;

import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.resilient.timeout.TimeouterManager;
import ru.tinkoff.kora.resilient.timeout.annotation.Timeout;
import ru.tinkoff.kora.resilient.timeout.telemetry.TimeoutMetrics;

import javax.annotation.Nullable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public interface TimeoutModule {

    default SimpleTimeoutConfig simpleTimeoutConfig(Config config, ConfigValueExtractor<SimpleTimeoutConfig> extractor) {
        var value = config.get("resilient");
        return extractor.extract(value);
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
