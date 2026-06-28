package io.koraframework.resilient.retry;

import io.koraframework.application.graph.All;
import io.koraframework.common.DefaultComponent;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractor;
import io.koraframework.resilient.retry.telemetry.RetryTelemetryFactory;
import io.koraframework.resilient.retry.telemetry.impl.DefaultRetryLoggerFactory;
import io.koraframework.resilient.retry.telemetry.impl.DefaultRetryMetricsFactory;
import io.koraframework.resilient.retry.telemetry.impl.DefaultRetryTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

public interface RetryModule {

    default RetryConfig koraRetryableConfig(Config config, ConfigValueExtractor<RetryConfig> extractor) {
        return extractor.extractOrThrow(config.get("resilient"));
    }

    default RetryManager koraRetryableManager(All<RetryPredicate> failurePredicates,
                                              RetryConfig config,
                                              RetryTelemetryFactory telemetryFactory) {
        return new KoraRetryManager(config, failurePredicates, telemetryFactory);
    }

    @DefaultComponent
    default RetryTelemetryFactory defaultRetryTelemetryFactory(@Nullable Tracer tracer,
                                                               @Nullable MeterRegistry meterRegistry,
                                                               @Nullable DefaultRetryLoggerFactory loggerFactory,
                                                               @Nullable DefaultRetryMetricsFactory metricsFactory) {
        return new DefaultRetryTelemetryFactory(tracer, meterRegistry, loggerFactory, metricsFactory);
    }

    @DefaultComponent
    default RetryPredicate defaultRetryFailurePredicate() {
        return new KoraRetryPredicate();
    }
}
