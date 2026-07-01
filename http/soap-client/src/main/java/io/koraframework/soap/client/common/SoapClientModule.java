package io.koraframework.soap.client.common;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.soap.client.common.telemetry.impl.DefaultSoapClientLoggerFactory;
import io.koraframework.soap.client.common.telemetry.impl.DefaultSoapClientMetricsFactory;
import io.koraframework.soap.client.common.telemetry.impl.DefaultSoapClientTelemetryFactory;

public interface SoapClientModule {

    @DefaultComponent
    default DefaultSoapClientTelemetryFactory defaultSoapClientTelemetryFactory(@Nullable Tracer tracer,
                                                                                @Nullable MeterRegistry meterRegistry,
                                                                                @Nullable DefaultSoapClientLoggerFactory loggerFactory,
                                                                                @Nullable DefaultSoapClientMetricsFactory metricsFactory) {
        return new DefaultSoapClientTelemetryFactory(tracer, meterRegistry, loggerFactory, metricsFactory);
    }
}
