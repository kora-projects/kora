package io.koraframework.soap.client.common;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;
import io.koraframework.common.DefaultComponent;
import io.koraframework.soap.client.common.telemetry.DefaultSoapClientTelemetryFactory;

public interface SoapClientModule {

    @DefaultComponent
    default DefaultSoapClientTelemetryFactory defaultSoapClientTelemetryFactory(@Nullable MeterRegistry meterRegistry, @Nullable Tracer tracer) {
        return new DefaultSoapClientTelemetryFactory(meterRegistry, tracer);
    }
}
