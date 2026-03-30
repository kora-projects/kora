package io.koraframework.http.client.common;

import io.koraframework.common.DefaultComponent;
import io.koraframework.config.common.Config;
import io.koraframework.config.common.extractor.ConfigValueExtractor;
import io.koraframework.http.client.common.request.mapper.HttpClientParameterWriterModule;
import io.koraframework.http.client.common.request.mapper.HttpClientRequestMapperModule;
import io.koraframework.http.client.common.response.HttpClientResponseMapperModule;
import io.koraframework.http.client.common.telemetry.impl.DefaultHttpClientTelemetryFactory;
import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

public interface HttpClientModule extends HttpClientRequestMapperModule, HttpClientResponseMapperModule, HttpClientParameterWriterModule {

    default HttpClientConfig httpClientConfig(Config config, ConfigValueExtractor<HttpClientConfig> configValueExtractor) {
        var configValue = config.get("httpClient");
        return configValueExtractor.extract(configValue);
    }

    @DefaultComponent
    default DefaultHttpClientTelemetryFactory defaultHttpClientTelemetryFactory(@Nullable Tracer tracer, @Nullable MeterRegistry meterRegistry) {
        return new DefaultHttpClientTelemetryFactory(tracer, meterRegistry);
    }
}
