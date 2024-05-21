package ru.tinkoff.kora.camunda.rest.telemetry;

import io.undertow.util.HeaderMap;
import jakarta.annotation.Nullable;

public interface CamundaRestTelemetry {

    interface CamundaRestTelemetryContext {

        void close(int statusCode, @Nullable Throwable exception);
    }

    CamundaRestTelemetryContext get(String method,
                                    String path,
                                    HeaderMap headerMap);
}
