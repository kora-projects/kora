package ru.tinkoff.kora.http.client.common.telemetry;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import ru.tinkoff.kora.http.common.HttpHeaders;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;

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
    public void logRequest(String authority, String method, String operation, String resolvedUri, @Nullable HttpHeaders headers, @Nullable String body) {
        var marker = StructuredArgument.marker("httpResponse", gen -> {
            gen.writeStartObject();
            gen.writeStringField("authority", authority);
            gen.writeStringField("operation", operation);
            gen.writeEndObject();
        });

        if (this.requestLog.isTraceEnabled() && body != null) {
            var headersString = this.requestHeaderString(headers);
            var bodyStr = this.requestBodyString(body);
            this.requestLog.trace(marker, "{} {}\n{}\n{}\n", method, resolvedUri, headersString, bodyStr);
            return;
        }
        if (this.requestLog.isDebugEnabled()) {
            var headersString = this.requestHeaderString(headers);
            this.requestLog.debug(marker, "{} {}\n{}\n", method, resolvedUri, headersString);
            return;
        }
        this.requestLog.info(marker, "{}", operation);
    }

    @Override
    public void logResponse(String authority, String operation, long processingTime, @Nullable Integer statusCode, HttpResultCode resultCode, @Nullable Throwable exception, @Nullable HttpHeaders headers, @Nullable String body) {
        var exceptionTypeString = exception != null
            ? exception.getClass().getCanonicalName()
            : statusCode != null ? null : CancellationException.class.getCanonicalName();

        var marker = StructuredArgument.marker("httpResponse", gen -> {
            gen.writeStartObject();
            gen.writeStringField("authority", authority);
            gen.writeStringField("operation", operation);
            gen.writeNumberField("processingTime", processingTime);
            gen.writeFieldName("statusCode");
            if (statusCode != null) {
                gen.writeNumber(statusCode);
            } else {
                gen.writeNull();
            }
            gen.writeStringField("resultCode", resultCode.name().toLowerCase());
            gen.writeStringField("exceptionType", exceptionTypeString);
            gen.writeEndObject();
        });
        if (responseLog.isTraceEnabled() && body != null) {
            var headersString = this.responseHeaderString(headers);
            var bodyStr = this.responseBodyString(body);
            responseLog.trace(marker, "{}\n{}\n{}\n", statusCode, headersString, bodyStr);
            return;
        }
        if (responseLog.isDebugEnabled() && headers != null) {
            var headersString = this.responseHeaderString(headers);
            responseLog.debug(marker, "{}\n{}\n", statusCode, headersString);
            return;
        }
        if (statusCode != null) {
            responseLog.info(marker, "{}", statusCode);
            return;
        }
        responseLog.info(marker, "Http response was not received");
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
