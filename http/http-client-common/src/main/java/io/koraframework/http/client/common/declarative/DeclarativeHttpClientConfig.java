package io.koraframework.http.client.common.declarative;

import org.jspecify.annotations.Nullable;
import io.koraframework.http.client.common.HttpClient;
import io.koraframework.http.client.common.interceptor.TelemetryInterceptor;
import io.koraframework.http.client.common.telemetry.HttpClientTelemetryConfig;
import io.koraframework.http.client.common.telemetry.HttpClientTelemetryFactory;

import java.time.Duration;

public interface DeclarativeHttpClientConfig {

    String url();

    HttpClientTelemetryConfig telemetry();

    @Nullable
    Duration requestTimeout();

    default DeclarativeHttpClientOperationData apply(HttpClient root, String clientName, Class<?> clientClass, String operationName, HttpClientOperationConfig operationConfig, HttpClientTelemetryFactory telemetryFactory, String operationPath) {
        var builder = root;
        var url = this.url() + operationPath;
        var requestTimeout = (this.requestTimeout() == null)
            ? null
            : this.requestTimeout();

        if (operationConfig.requestTimeout() != null) {
            requestTimeout = operationConfig.requestTimeout();
        }
        var telemetryConfig = new HttpClientOperationTelemetryConfig(telemetry(), operationConfig.telemetry());

        var telemetry = telemetryFactory.get(clientName, clientClass.getCanonicalName() + "." + operationName, telemetryConfig);
        if (telemetry != null) {
            builder = builder.with(new TelemetryInterceptor(telemetry));
        }
        return new DeclarativeHttpClientOperationData(builder, url, requestTimeout);
    }
}
