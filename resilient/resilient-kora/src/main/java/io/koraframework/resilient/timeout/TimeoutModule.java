package io.koraframework.resilient.timeout;

import io.koraframework.common.DefaultComponent;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractor;
import io.koraframework.resilient.timeout.telemetry.TimeoutTelemetryFactory;
import io.koraframework.resilient.timeout.telemetry.impl.DefaultTimeoutLoggerFactory;
import io.koraframework.resilient.timeout.telemetry.impl.DefaultTimeoutMetricsFactory;
import io.koraframework.resilient.timeout.telemetry.impl.DefaultTimeoutTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

public interface TimeoutModule {

    default TimeoutConfig koraTimeoutConfig(Config config, ConfigValueExtractor<TimeoutConfig> extractor) {
        return extractor.extractOrThrow(config.get("resilient"));
    }

    default TimeoutManager koraTimeoutManager(TimeoutConfig config,
                                              TimeoutTelemetryFactory telemetryFactory) {
        return new KoraTimeoutManager(config, telemetryFactory);
    }

    @DefaultComponent
    default TimeoutTelemetryFactory defaultTimeoutTelemetryFactory(@Nullable Tracer tracer,
                                                                   @Nullable MeterRegistry meterRegistry,
                                                                   @Nullable DefaultTimeoutLoggerFactory loggerFactory,
                                                                   @Nullable DefaultTimeoutMetricsFactory metricsFactory) {
        return new DefaultTimeoutTelemetryFactory(tracer, meterRegistry, loggerFactory, metricsFactory);
    }
}
