package ru.tinkoff.kora.camunda.rest.telemetry;

import jakarta.annotation.Nullable;

public interface CamundaRestMetrics {

    void requestStarted(String method,
                        String path);

    void requestFinished(String method,
                         String path,
                         int statusCode,
                         long processingTimeNano,
                         @Nullable Throwable exception);
}
