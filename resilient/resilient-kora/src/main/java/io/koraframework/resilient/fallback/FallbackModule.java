package io.koraframework.resilient.fallback;

import io.koraframework.application.graph.All;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.mapper.ConfigValueMapper;
import io.koraframework.resilient.fallback.telemetry.FallbackTelemetryFactory;
import io.koraframework.resilient.fallback.telemetry.impl.DefaultFallbackLoggerFactory;
import io.koraframework.resilient.fallback.telemetry.impl.DefaultFallbackMetricsFactory;
import io.koraframework.resilient.fallback.telemetry.impl.DefaultFallbackTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

public interface FallbackModule {

    default FallbackConfig koraFallbackConfig(Config config, ConfigValueMapper<FallbackConfig> mapper) {
        return mapper.mapOrThrow(config.get("resilient"));
    }

    default FallbackManager koraFallbackManager(FallbackConfig config,
                                                All<FallbackPredicate> failurePredicates,
                                                FallbackTelemetryFactory telemetryFactory) {
        return new KoraFallbackManager(config, failurePredicates, telemetryFactory);
    }

    @DefaultComponent
    default FallbackTelemetryFactory defaultFallbackTelemetryFactory(@Nullable Tracer tracer,
                                                                     @Nullable MeterRegistry meterRegistry,
                                                                     @Nullable DefaultFallbackLoggerFactory loggerFactory,
                                                                     @Nullable DefaultFallbackMetricsFactory metricsFactory) {
        return new DefaultFallbackTelemetryFactory(tracer, meterRegistry, loggerFactory, metricsFactory);
    }

    default FallbackPredicate defaultFallbackFailurePredicate() {
        return new KoraFallbackPredicate();
    }
}
