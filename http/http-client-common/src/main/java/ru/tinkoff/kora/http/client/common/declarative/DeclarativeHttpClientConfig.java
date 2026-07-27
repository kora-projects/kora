package ru.tinkoff.kora.http.client.common.declarative;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.client.common.HttpClient;
import ru.tinkoff.kora.http.client.common.interceptor.TelemetryInterceptor;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTelemetryFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.time.Duration;

public interface DeclarativeHttpClientConfig {

    /**
     * @return Base service URL where requests will be sent.
     */
    String url();

    /**
     * @return Telemetry configuration for logging, metrics and tracing of client requests.
     */
    TelemetryConfig telemetry();

    /**
     * @return Maximum request time that may include DNS resolution, connection, request body write, server processing and response body read.
     */
    @Nullable
    Duration requestTimeout();

    default DeclarativeHttpClientOperationData apply(HttpClient root, Class<?> clientClass, String operationName, HttpClientOperationConfig operationConfig, HttpClientTelemetryFactory telemetryFactory, String operationPath) {
        var builder = root;
        var url = this.url() + operationPath;
        var requestTimeout = (this.requestTimeout() == null)
            ? null
            : this.requestTimeout();

        if (operationConfig.requestTimeout() != null) {
            requestTimeout = operationConfig.requestTimeout();
        }
        var telemetryConfig = new HttpClientOperationTelemetryConfig(telemetry(), operationConfig.telemetry());

        var telemetry = telemetryFactory.get(telemetryConfig, clientClass.getCanonicalName() + "." + operationName);
        if (telemetry != null) {
            builder = builder.with(new TelemetryInterceptor(telemetry));
        }
        return new DeclarativeHttpClientOperationData(builder, url, requestTimeout);
    }
}
