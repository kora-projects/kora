package io.koraframework.resilient.bulkhead;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.resilient.bulkhead.telemetry.BulkheadTelemetryFactory;
import io.koraframework.resilient.bulkhead.telemetry.impl.DefaultBulkheadLoggerFactory;
import io.koraframework.resilient.bulkhead.telemetry.impl.DefaultBulkheadMetricsFactory;
import io.koraframework.resilient.bulkhead.telemetry.impl.DefaultBulkheadTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

public interface BulkheadModule {

    @DefaultComponent
    default BulkheadTelemetryFactory defaultBulkheadTelemetryFactory(
        @Nullable Tracer tracer,
        @Nullable MeterRegistry meterRegistry,
        @Nullable DefaultBulkheadLoggerFactory loggerFactory,
        @Nullable DefaultBulkheadMetricsFactory metricsFactory
    ) {
        return new DefaultBulkheadTelemetryFactory(tracer, meterRegistry, loggerFactory, metricsFactory);
    }
}
