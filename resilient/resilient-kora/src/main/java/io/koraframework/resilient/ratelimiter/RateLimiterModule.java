package io.koraframework.resilient.ratelimiter;

import io.koraframework.common.DefaultComponent;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractionException;
import io.koraframework.config.common.extractor.ConfigValueExtractor;
import io.koraframework.resilient.ratelimiter.telemetry.RateLimiterTelemetryFactory;
import io.koraframework.resilient.ratelimiter.telemetry.impl.DefaultRateLimiterLoggerFactory;
import io.koraframework.resilient.ratelimiter.telemetry.impl.DefaultRateLimiterMetricsFactory;
import io.koraframework.resilient.ratelimiter.telemetry.impl.DefaultRateLimiterTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public interface RateLimiterModule {

    default RateLimiterConfig koraRateLimiterConfig(Config config, ConfigValueExtractor<RateLimiterConfig> extractor) {
        return extractor.extractOrThrow(config.get("resilient"));
    }

    default RateLimiterManager koraRateLimiterManager(RateLimiterConfig config,
                                                      RateLimiterTelemetryFactory telemetryFactory) {
        return new KoraRateLimiterManager(config, telemetryFactory);
    }

    @DefaultComponent
    default RateLimiterTelemetryFactory defaultRateLimiterTelemetryFactory(@Nullable Tracer tracer,
                                                                           @Nullable MeterRegistry meterRegistry,
                                                                           @Nullable DefaultRateLimiterLoggerFactory loggerFactory,
                                                                           @Nullable DefaultRateLimiterMetricsFactory metricsFactory) {
        return new DefaultRateLimiterTelemetryFactory(tracer, meterRegistry, loggerFactory, metricsFactory);
    }
}
