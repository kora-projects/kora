package ru.tinkoff.kora.resilient.timeout;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.application.graph.internal.loom.VirtualThreadExecutorHolder;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;

import java.util.concurrent.Executors;

public interface TimeoutModule {

    default TimeoutConfig koraTimeoutConfig(Config config, ConfigValueExtractor<TimeoutConfig> extractor) {
        var value = config.get("resilient");
        return extractor.extract(value);
    }

    default TimeoutManager koraTimeoutManager(TimeoutExecutor timeoutExecutor,
                                              TimeoutConfig config,
                                              @Nullable TimeoutMetrics metrics) {
        TimeoutMetrics timeoutMetrics = (metrics == null) ? new NoopTimeoutMetrics() : metrics;
        return new KoraTimeoutManager(timeoutMetrics, timeoutExecutor, config);
    }

    @DefaultComponent
    default TimeoutExecutor koraTimeoutExecutorService() {
        return (VirtualThreadExecutorHolder.status() == VirtualThreadExecutorHolder.VirtualThreadStatus.ENABLED)
            ? new TimeoutExecutor(VirtualThreadExecutorHolder.executor())
            : new TimeoutExecutor(Executors.newCachedThreadPool());
    }
}
