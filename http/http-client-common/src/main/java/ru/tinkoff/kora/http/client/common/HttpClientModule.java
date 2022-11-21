package ru.tinkoff.kora.http.client.common;

import com.typesafe.config.Config;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.config.common.extractor.ConfigValueExtractor;
import ru.tinkoff.kora.http.client.common.form.FormMultipartClientRequestMapper;
import ru.tinkoff.kora.http.client.common.form.FormUrlEncodedClientRequestMapper;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequestMapperModule;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponseMapperModule;
import ru.tinkoff.kora.http.client.common.telemetry.*;

import javax.annotation.Nullable;

public interface HttpClientModule extends HttpClientRequestMapperModule, HttpClientResponseMapperModule, ParameterConvertersModule {
    default HttpClientConfig httpClientConfig(Config config, ConfigValueExtractor<HttpClientConfig> configValueExtractor) {
        if (config.hasPath("httpClient")) {
            var configValue = config.getValue("httpClient");
            return configValueExtractor.extract(configValue);

        } else {
            return new HttpClientConfig(null, null, null, null);
        }
    }

    @DefaultComponent
    default Sl4fjHttpClientLoggerFactory sl4fjHttpClientLoggerFactory() {
        return new Sl4fjHttpClientLoggerFactory();
    }

    @DefaultComponent
    default DefaultHttpClientTelemetryFactory defaultHttpClientTelemetryFactory(@Nullable HttpClientLoggerFactory loggerFactory, @Nullable HttpClientTracerFactory tracingFactory, @Nullable HttpClientMetricsFactory metricsFactory) {
        return new DefaultHttpClientTelemetryFactory(loggerFactory, tracingFactory, metricsFactory);
    }

    default FormUrlEncodedClientRequestMapper formUrlEncodedClientRequestMapper() {
        return new FormUrlEncodedClientRequestMapper();
    }

    default FormMultipartClientRequestMapper formMultipartClientRequestMapper() {
        return new FormMultipartClientRequestMapper();
    }
}
