package ru.tinkoff.kora.http.client.common;

import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.config.common.Config;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.http.client.common.form.FormMultipartClientRequestMapper;
import ru.tinkoff.kora.http.client.common.form.FormUrlEncodedClientRequestMapper;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequestMapperModule;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapperModule;
import ru.tinkoff.kora.http.client.common.telemetry.*;

import jakarta.annotation.Nullable;

public interface HttpClientModule extends HttpClientRequestMapperModule, HttpClientResponseMapperModule, ParameterConvertersModule {
    default HttpClientConfig httpClientConfig(Config config, ConfigValueExtractor<HttpClientConfig> configValueExtractor) {
        var configValue = config.get("httpClient");
        return configValueExtractor.extract(configValue);
    }

    default HttpClientTelemetryConfig httpClientTelemetryConfig(Config config, ConfigValueExtractor<HttpClientTelemetryConfig> configValueExtractor) {
        var configValue = config.get("httpClient.telemetry");
        return configValueExtractor.extract(configValue);
    }

    @DefaultComponent
    default Sl4fjHttpClientLoggerFactory sl4fjHttpClientLoggerFactory(HttpClientTelemetryConfig config) {
        return new Sl4fjHttpClientLoggerFactory(config);
    }

    @DefaultComponent
    default DefaultHttpClientTelemetryFactory defaultHttpClientTelemetryFactory(@Nullable HttpClientLoggerFactory loggerFactory, @Nullable HttpClientTracerFactory tracingFactory, @Nullable HttpClientMetricsFactory metricsFactory) {
        return new DefaultHttpClientTelemetryFactory(loggerFactory, tracingFactory, metricsFactory);
    }

}
