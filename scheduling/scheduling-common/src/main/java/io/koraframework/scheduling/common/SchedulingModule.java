package io.koraframework.scheduling.common;

import io.koraframework.common.DefaultComponent;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractor;
import io.koraframework.scheduling.common.telemetry.SchedulingTelemetryConfig;
import io.koraframework.scheduling.common.telemetry.SchedulingTelemetryFactory;
import io.koraframework.scheduling.common.telemetry.impl.DefaultSchedulingLoggerFactory;
import io.koraframework.scheduling.common.telemetry.impl.DefaultSchedulingMetricsFactory;
import io.koraframework.scheduling.common.telemetry.impl.DefaultSchedulingTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

public interface SchedulingModule {

    default SchedulingTelemetryConfig schedulingTelemetryConfig(Config config, ConfigValueExtractor<SchedulingTelemetryConfig> extractor) {
        return extractor.extractOrThrow(config.get("scheduling.telemetry"));
    }

    @DefaultComponent
    default SchedulingTelemetryFactory defaultSchedulingTelemetryFactory(SchedulingTelemetryConfig config,
                                                                         @Nullable Tracer tracer,
                                                                         @Nullable MeterRegistry meterRegistry,
                                                                         @Nullable DefaultSchedulingLoggerFactory loggerFactory,
                                                                         @Nullable DefaultSchedulingMetricsFactory metricsFactory) {
        return new DefaultSchedulingTelemetryFactory(config, tracer, meterRegistry, loggerFactory, metricsFactory);
    }
}
