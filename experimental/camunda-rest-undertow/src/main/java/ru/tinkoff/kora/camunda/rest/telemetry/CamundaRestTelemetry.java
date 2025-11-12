package ru.tinkoff.kora.camunda.rest.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.body.HttpBodyInput;
import ru.tinkoff.kora.http.common.header.HttpHeaders;

import java.util.Collection;
import java.util.Map;

public interface CamundaRestTelemetry {

    CamundaRestTelemetryContext EMPTY_CTX = (s, r, h, ex) -> {};
    CamundaRestTelemetry EMPTY = (s, host, m, p, pt, h, q, b) -> EMPTY_CTX;

    interface CamundaRestTelemetryContext {

        void close(int statusCode, HttpResultCode resultCode, HttpHeaders headers, @Nullable Throwable exception);
    }

    CamundaRestTelemetryContext get(String scheme,
                                    String hostName,
                                    String method,
                                    String path,
                                    @Nullable String pathTemplate,
                                    HttpHeaders headers,
                                    Map<String, ? extends Collection<String>> queryParams,
                                    @Nullable HttpBodyInput body);
}
