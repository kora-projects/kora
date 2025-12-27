package ru.tinkoff.kora.scheduling.common;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.scheduling.common.telemetry.DefaultSchedulingTelemetryFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

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
