package ru.tinkoff.kora.camunda.rest.telemetry;

import io.undertow.util.HeaderMap;
import jakarta.annotation.Nullable;

public interface CamundaRestTracer {

    interface CamundaRestSpan {

        void close(int statusCode, @Nullable Throwable exception);
    }

    CamundaRestSpan createSpan(String method,
                               String path,
                               HeaderMap headerMap);
}
