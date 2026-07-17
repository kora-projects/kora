package io.koraframework.http.client.common;

import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.http.client.common.request.mapper.HttpClientParameterWriterModule;
import io.koraframework.http.client.common.request.mapper.HttpClientRequestMapperModule;
import io.koraframework.http.client.common.response.HttpClientResponseMapperModule;
import io.koraframework.http.client.common.telemetry.HttpClientTelemetryFactory;
import io.koraframework.http.client.common.telemetry.impl.DefaultHttpClientBodyConverter;
import io.koraframework.http.client.common.telemetry.impl.DefaultHttpClientLoggerFactory;
import io.koraframework.http.client.common.telemetry.impl.DefaultHttpClientMetricsFactory;
import io.koraframework.http.client.common.telemetry.impl.DefaultHttpClientTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

public interface HttpClientModule extends HttpClientRequestMapperModule, HttpClientResponseMapperModule, HttpClientParameterWriterModule {

    @DefaultComponent
    default HttpClientTelemetryFactory defaultHttpClientTelemetryFactory(@Nullable Tracer tracer,
                                                                         @Nullable MeterRegistry meterRegistry,
                                                                         @Nullable DefaultHttpClientLoggerFactory loggerFactory,
                                                                         @Nullable DefaultHttpClientMetricsFactory metricsFactory,
                                                                         @Nullable DefaultHttpClientBodyConverter loggerBodyConverter) {
        return new DefaultHttpClientTelemetryFactory(tracer, meterRegistry, loggerFactory, metricsFactory, loggerBodyConverter);
    }
}
