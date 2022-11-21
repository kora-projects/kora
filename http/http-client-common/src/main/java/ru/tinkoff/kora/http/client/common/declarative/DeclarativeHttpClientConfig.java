package ru.tinkoff.kora.http.client.common.declarative;

import ru.tinkoff.kora.http.client.common.HttpClient;
import ru.tinkoff.kora.http.client.common.interceptor.TelemetryInterceptor;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTelemetryFactory;

import javax.annotation.Nullable;

public interface DeclarativeHttpClientConfig {
    String url();

    @Nullable
    Integer requestTimeout();

    default DeclarativeHttpClientOperationData apply(HttpClient root, Class<?> clientClass, String operationName, @Nullable HttpClientOperationConfig operationConfig, HttpClientTelemetryFactory telemetryFactory, String operationPath) {
        var builder = root;
        var url = this.url() + operationPath;
        var requestTimeout = this.requestTimeout();
        if (operationConfig != null && operationConfig.requestTimeout() > 0) {
            requestTimeout = operationConfig.requestTimeout();
        } else if (requestTimeout == null) {
            requestTimeout = -1;
        }

        var telemetry = telemetryFactory.get(clientClass.getCanonicalName() + "." + operationName);
        if (telemetry != null) {
            builder = builder.with(new TelemetryInterceptor(telemetry));
        }
        return new DeclarativeHttpClientOperationData(builder, url, requestTimeout);
    }
}
