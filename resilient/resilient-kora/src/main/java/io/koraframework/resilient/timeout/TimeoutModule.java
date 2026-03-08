package io.koraframework.resilient.timeout;

import org.jspecify.annotations.Nullable;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractor;

public interface TimeoutModule {

    default TimeoutConfig koraTimeoutConfig(Config config, ConfigValueExtractor<TimeoutConfig> extractor) {
        var value = config.get("resilient");
        return extractor.extract(value);
    }

    default TimeoutManager koraTimeoutManager(TimeoutConfig config,
                                              @Nullable TimeoutMetrics metrics) {
        TimeoutMetrics timeoutMetrics = (metrics == null) ? new NoopTimeoutMetrics() : metrics;
        return new KoraTimeoutManager(timeoutMetrics, config);
    }
}
