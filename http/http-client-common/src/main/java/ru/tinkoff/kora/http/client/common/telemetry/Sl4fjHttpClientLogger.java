package ru.tinkoff.kora.http.client.common.telemetry;

import org.slf4j.Logger;
import ru.tinkoff.kora.http.common.HttpHeaders;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;

import javax.annotation.Nullable;
import java.util.concurrent.CancellationException;

public class Sl4fjHttpClientLogger implements HttpClientLogger {
    private final Logger requestLog;
    private final Logger responseLog;

    public Sl4fjHttpClientLogger(Logger requestLog, Logger responseLog) {
        this.requestLog = requestLog;
        this.responseLog = responseLog;
    }

    @Override
    public boolean logRequest() {
        return this.requestLog.isInfoEnabled();
    }

    @Override
    public boolean logRequestHeaders() {
        return this.requestLog.isDebugEnabled();
    }

    @Override
    public boolean logRequestBody() {
        return this.requestLog.isTraceEnabled();
    }

    @Override
    public boolean logResponse() {
        return this.responseLog.isInfoEnabled();
    }

    @Override
    public boolean logResponseHeaders() {
        return this.responseLog.isDebugEnabled();
    }

    @Override
    public boolean logResponseBody() {
        return this.responseLog.isTraceEnabled();
    }

    @Override
    public void logRequest(String authority,
                           String method,
                           String operation,
                           String resolvedUri,
                           @Nullable HttpHeaders headers,
                           @Nullable String body) {
        var marker = StructuredArgument.marker("httpResponse", gen -> {
            gen.writeStartObject();
            gen.writeStringField("authority", authority);
            gen.writeStringField("operation", operation);
            gen.writeEndObject();
        });

        if (this.requestLog.isTraceEnabled() && headers != null && headers.size() > 0 && body != null) {
            var headersString = this.requestHeaderString(headers);
            var bodyStr = this.requestBodyString(body);
            this.requestLog.trace(marker, "Requesting {}\n{}\n{}", operation, headersString, bodyStr);
        } else if (this.requestLog.isDebugEnabled() && headers != null && headers.size() > 0) {
            var headersString = this.requestHeaderString(headers);
            this.requestLog.debug(marker, "Requesting {}\n{}", operation, headersString);
        } else {
            this.requestLog.info(marker, "Requesting {}", operation);
        }
    }

    @Override
    public void logResponse(String authority,
                            String operation,
                            long processingTime,
                            @Nullable Integer statusCode,
                            HttpResultCode resultCode,
                            @Nullable Throwable exception,
                            @Nullable HttpHeaders headers,
                            @Nullable String body) {
        var exceptionTypeString = exception != null
            ? exception.getClass().getCanonicalName()
            : statusCode != null ? null : CancellationException.class.getCanonicalName();

        var marker = StructuredArgument.marker("httpResponse", gen -> {
            gen.writeStartObject();
            gen.writeStringField("authority", authority);
            gen.writeStringField("operation", operation);
            gen.writeStringField("resultCode", resultCode.name().toLowerCase());
            gen.writeNumberField("processingTime", processingTime / 1_000_000);
            if (statusCode != null) {
                gen.writeFieldName("statusCode");
                gen.writeNumber(statusCode);
            }
            if (exceptionTypeString != null) {
                gen.writeStringField("exceptionType", exceptionTypeString);
            }
            gen.writeEndObject();
        });

        if (responseLog.isTraceEnabled() && headers != null && headers.size() > 0 && body != null) {
            var headersString = this.responseHeaderString(headers);
            var bodyStr = this.responseBodyString(body);
            responseLog.trace(marker, "Received {} from {}\n{}\n{}", statusCode, operation, headersString, bodyStr);
        } else if (responseLog.isDebugEnabled() && headers != null && headers.size() > 0) {
            var headersString = this.responseHeaderString(headers);
            responseLog.debug(marker, "Received {} from {}\n{}", statusCode, operation, headersString);
        } else if (statusCode != null) {
            responseLog.info(marker, "Received {} from {}", statusCode, operation);
        } else {
            responseLog.info(marker, "Received no HttpResponse from {}", operation);
        }
    }

    public String responseBodyString(String body) {
        return body;
    }

    public String responseHeaderString(HttpHeaders headers) {
        return HttpHeaders.toString(headers);
    }

    public String requestBodyString(String body) {
        return body;
    }

    public String requestHeaderString(HttpHeaders headers) {
        return HttpHeaders.toString(headers);
    }
}
