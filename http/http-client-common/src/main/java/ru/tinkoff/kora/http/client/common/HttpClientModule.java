package ru.tinkoff.kora.http.client.common;

import io.micrometer.core.instrument.MeterRegistry;
import io.opentelemetry.api.trace.Tracer;
import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequestMapperModule;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapperModule;
import ru.tinkoff.kora.http.client.common.telemetry.impl.DefaultHttpClientTelemetryFactory;

public interface HttpClientModule extends HttpClientRequestMapperModule, HttpClientResponseMapperModule, ParameterConvertersModule {
    default HttpClientConfig httpClientConfig(Config config, ConfigValueExtractor<HttpClientConfig> configValueExtractor) {
        var configValue = config.get("httpClient");
        return configValueExtractor.extract(configValue);
    }

    @DefaultComponent
    default DefaultHttpClientTelemetryFactory defaultHttpClientTelemetryFactory(@Nullable Tracer tracer, @Nullable MeterRegistry meterRegistry) {
        return new DefaultHttpClientTelemetryFactory(tracer, meterRegistry);
    }

}
