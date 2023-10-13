package ru.tinkoff.kora.scheduling.common;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.common.Tag;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.scheduling.common.telemetry.DefaultSchedulingTelemetryFactory;
import ru.tinkoff.kora.scheduling.common.telemetry.SchedulingLoggerFactory;
import ru.tinkoff.kora.scheduling.common.telemetry.SchedulingMetricsFactory;
import ru.tinkoff.kora.scheduling.common.telemetry.SchedulingTracerFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public interface SchedulingModule {
    @Tag(SchedulingModule.class)
    default TelemetryConfig defaultSchedulingConfig(Config config, ConfigValueExtractor<TelemetryConfig> extractor) {
        return Objects.requireNonNull(extractor.extract(config.get("scheduling.telemetry")));
    }

    @DefaultComponent
    default DefaultSchedulingTelemetryFactory defaultSchedulingTelemetryFactory(
        @Tag(SchedulingModule.class) TelemetryConfig config,
        @Nullable SchedulingMetricsFactory metrics,
        @Nullable SchedulingTracerFactory tracer,
        @Nullable SchedulingLoggerFactory logger) {
        return new DefaultSchedulingTelemetryFactory(config, metrics, tracer, logger);
    }
}
