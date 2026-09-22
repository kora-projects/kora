package io.koraframework.http.client.common;

import io.koraframework.application.graph.All;
import io.koraframework.common.annotation.DefaultComponent;
import io.koraframework.common.annotation.Tag;
import io.koraframework.http.client.common.request.mapper.HttpClientParameterWriterModule;
import io.koraframework.http.client.common.request.mapper.HttpClientRequestMapperModule;
import io.koraframework.http.client.common.response.HttpClientResponseMapperModule;
import io.koraframework.http.client.common.telemetry.HttpClientTelemetryFactory;
import io.koraframework.http.client.common.telemetry.HttpClientTelemetry;
import io.koraframework.http.client.common.telemetry.impl.DefaultHttpClientBodyConverter;
import io.koraframework.http.client.common.telemetry.impl.DefaultHttpClientLoggerFactory;
import io.koraframework.http.client.common.telemetry.impl.DefaultHttpClientMetricsFactory;
import io.koraframework.http.client.common.telemetry.impl.DefaultHttpClientTelemetryFactory;
import io.koraframework.logging.common.masking.MaskingStrategy;
import io.koraframework.logging.common.masking.raw.DataMasker;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

import java.util.stream.StreamSupport;

public interface HttpClientModule extends HttpClientRequestMapperModule, HttpClientResponseMapperModule, HttpClientParameterWriterModule {

    @Tag(HttpClientTelemetry.class)
    @DefaultComponent
    default MaskingStrategy defaultHttpClientArgMaskingStrategy() {
        return value -> "***";
    }

    @DefaultComponent
    default DefaultHttpClientLoggerFactory defaultHttpClientLoggerFactory(@Tag(HttpClientTelemetry.class) MaskingStrategy maskingStrategy) {
        return new DefaultHttpClientLoggerFactory(maskingStrategy);
    }

    @DefaultComponent
    default DefaultHttpClientBodyConverter defaultHttpClientBodyConverter(@Tag(HttpClientTelemetry.class) All<DataMasker> dataMaskers) {
        return new DefaultHttpClientBodyConverter(StreamSupport.stream(dataMaskers.spliterator(), false).toList());
    }

    @DefaultComponent
    default HttpClientTelemetryFactory defaultHttpClientTelemetryFactory(@Nullable Tracer tracer,
                                                                         @Nullable MeterRegistry meterRegistry,
                                                                         @Nullable DefaultHttpClientLoggerFactory loggerFactory,
                                                                         @Nullable DefaultHttpClientMetricsFactory metricsFactory,
                                                                         DefaultHttpClientBodyConverter loggerBodyConverter) {
        return new DefaultHttpClientTelemetryFactory(tracer, meterRegistry, loggerFactory, metricsFactory, loggerBodyConverter);
    }
}
