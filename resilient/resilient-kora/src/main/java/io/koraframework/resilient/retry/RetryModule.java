package io.koraframework.resilient.retry;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.resilient.retry.telemetry.RetryTelemetryFactory;
import io.koraframework.resilient.retry.telemetry.impl.DefaultRetryLoggerFactory;
import io.koraframework.resilient.retry.telemetry.impl.DefaultRetryMetricsFactory;
import io.koraframework.resilient.retry.telemetry.impl.DefaultRetryTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

public interface RetryModule {

    @DefaultComponent
    default RetryTelemetryFactory defaultRetryTelemetryFactory(@Nullable Tracer tracer,
                                                               @Nullable MeterRegistry meterRegistry,
                                                               @Nullable DefaultRetryLoggerFactory loggerFactory,
                                                               @Nullable DefaultRetryMetricsFactory metricsFactory) {
        return new DefaultRetryTelemetryFactory(tracer, meterRegistry, loggerFactory, metricsFactory);
    }
}
