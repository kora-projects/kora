package io.koraframework.http.client.common.telemetry;

import io.koraframework.http.client.common.request.HttpClientRequest;

public interface HttpClientTelemetry {
    HttpClientObservation observe(HttpClientRequest request);
}
