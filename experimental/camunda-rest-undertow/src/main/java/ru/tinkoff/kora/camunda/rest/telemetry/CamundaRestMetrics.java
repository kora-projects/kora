package ru.tinkoff.kora.camunda.rest.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

public interface CamundaRestMetrics {

    void requestStarted(String method, String pathTemplate, String host, String scheme);

    void requestFinished(int statusCode,
                         HttpResultCode resultCode,
                         String scheme,
                         String host,
                         String method,
                         String pathTemplate,
                         HttpHeaders headers,
                         long processingTimeNanos,
                         @Nullable Throwable exception);
}
