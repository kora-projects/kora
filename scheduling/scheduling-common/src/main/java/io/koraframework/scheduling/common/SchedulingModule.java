package io.koraframework.scheduling.common;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;
import io.koraframework.common.DefaultComponent;
import io.koraframework.common.Tag;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractor;
import io.koraframework.scheduling.common.telemetry.DefaultSchedulingTelemetryFactory;
import io.koraframework.telemetry.common.TelemetryConfig;

import java.util.Objects;

public interface SchedulingModule {
    @Tag(SchedulingModule.class)
    default TelemetryConfig defaultSchedulingConfig(Config config, ConfigValueExtractor<TelemetryConfig> extractor) {
        return Objects.requireNonNull(extractor.extract(config.get("scheduling.telemetry")));
    }

    @DefaultComponent
    default DefaultSchedulingTelemetryFactory defaultSchedulingTelemetryFactory(TelemetryConfig config, @Nullable MeterRegistry meterRegistry, @Nullable Tracer tracer) {
        return new DefaultSchedulingTelemetryFactory(config, meterRegistry, tracer);
    }
}
