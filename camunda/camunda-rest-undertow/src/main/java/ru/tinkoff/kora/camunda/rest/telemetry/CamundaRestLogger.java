package ru.tinkoff.kora.camunda.rest.telemetry;


import jakarta.annotation.Nullable;

public interface CamundaRestLogger {

    void logStart(String method,
                  String path);

    void logEnd(String method,
                String path,
                int statusCode,
                long processingTimeInNanos,
                @Nullable Throwable exception);
}
