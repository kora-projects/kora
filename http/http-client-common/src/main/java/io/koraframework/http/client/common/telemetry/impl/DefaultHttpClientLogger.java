package io.koraframework.http.client.common.telemetry.impl;

import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.client.common.response.HttpClientResponse;
import io.koraframework.http.client.common.telemetry.HttpClientTelemetryConfig;
import io.koraframework.http.common.HttpResultCode;
import io.koraframework.http.common.telemetry.MaskingUtils;
import io.koraframework.logging.common.arg.StructuredArgumentWriter;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.event.Level;

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultHttpClientLogger {

    protected final HttpClientTelemetryConfig.HttpClientLoggerConfig config;
    protected final String clientConfigPath;
    protected final String clientCanonicalName;
    protected final Logger requestLog;
    protected final Logger responseLog;
    protected final DefaultHttpClientBodyConverter bodyLogger;
    protected final Set<String> maskedQueryParams;
    protected final Set<String> maskedHeaders;
    protected final String mask;
    @Nullable
    protected final Boolean pathTemplate;

    public DefaultHttpClientLogger(String clientConfigPath,
                                   String clientCanonicalName,
                                   Logger requestLog,
                                   Logger responseLog,
                                   DefaultHttpClientBodyConverter bodyLogger,
                                   HttpClientTelemetryConfig.HttpClientLoggerConfig config) {
        this.clientConfigPath = clientConfigPath;
        this.clientCanonicalName = clientCanonicalName;
        this.bodyLogger = bodyLogger;
        this.requestLog = requestLog;
        this.responseLog = responseLog;
        this.maskedQueryParams = config.maskQueries().stream()
            .map(e -> e.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
        this.maskedHeaders = config.maskHeaders().stream()
            .map(e -> e.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
        this.pathTemplate = config.pathTemplate();
        this.mask = config.mask();
        this.config = config;
    }

    public boolean logRequestBody() {
        return this.requestLog.isTraceEnabled();
    }

    public boolean logResponseBody() {
        return this.responseLog.isTraceEnabled();
    }

    public void logRequest(HttpClientRequest rq,
                           @Nullable ByteBuffer body,
                           @Nullable String contentType) {
        if (!requestLog.isInfoEnabled()) {
            return;
        }
        var level = Level.INFO;
        var queryParams = rq.uri().getQuery();
        var headers = rq.headers();
        if (!requestLog.isDebugEnabled()) {
            queryParams = null;
            headers = null;
        } else {
            level = Level.DEBUG;
        }
        if (!requestLog.isTraceEnabled()) {
            body = null;
        } else {
            level = Level.TRACE;
        }
        var finalQuery = queryParams;
        var finalHeaders = headers;
        var operation = getOperation(requestLog, rq.method(), rq.uri().getPath(), rq.uriTemplate());
        var finalBody = (body != null && body.remaining() > 0)
            ? bodyLogger.convertRequestBody(clientConfigPath, clientCanonicalName, rq, body, contentType)
            : null;
        var arg = (StructuredArgumentWriter) gen -> {
            gen.writeStartObject();
            gen.writeStringProperty("authority", rq.uri().getAuthority());
            gen.writeStringProperty("operation", operation);
            if (finalQuery != null && !finalQuery.isEmpty()) {
                gen.writeStringProperty("queryParams", MaskingUtils.toMaskedString(maskedQueryParams, mask, finalQuery));
            }
            if (finalHeaders != null && !finalHeaders.isEmpty()) {
                gen.writeStringProperty("headers", MaskingUtils.toMaskedString(maskedHeaders, mask, finalHeaders));
            }
            if (finalBody != null) {
                gen.writeStringProperty("body", finalBody);
            }
            gen.writeEndObject();
        };

        requestLog.atLevel(level)
            .addKeyValue("httpRequest", arg)
            .addKeyValue("clientConfigPath", clientConfigPath)
            .log("HttpClient request started");
    }

    public void logResponse(HttpClientRequest rq,
                            HttpClientResponse rs,
                            long processingTookNanos,
                            @Nullable ByteBuffer body,
                            @Nullable String contentType) {
        if (!responseLog.isInfoEnabled()) {
            return;
        }

        var level = Level.INFO;
        var headers = rs == null ? null : rs.headers();
        if (!responseLog.isDebugEnabled()) {
            headers = null;
        } else {
            level = Level.DEBUG;
        }
        if (!responseLog.isTraceEnabled()) {
            body = null;
        } else {
            level = Level.TRACE;
        }
        var finalHeaders = headers;
        var operation = getOperation(responseLog, rq.method(), rq.uri().getPath(), rq.uriTemplate());
        var finalBody = (body != null && body.remaining() > 0)
            ? bodyLogger.convertResponseBody(clientConfigPath, clientCanonicalName, rs, body, contentType)
            : null;
        var statusCode = rs != null
            ? rs.code()
            : null;

        var arg = (StructuredArgumentWriter) gen -> {
            gen.writeStartObject();
            gen.writeStringProperty("authority", rq.uri().getAuthority());
            gen.writeStringProperty("operation", operation);
            gen.writeStringProperty("resultCode", HttpResultCode.fromStatusCode(rs.code()).string());
            gen.writeNumberProperty("processingTime", processingTookNanos / 1_000_000);
            if (statusCode != null) {
                gen.writeNumberProperty("statusCode", statusCode);
            }
            if (finalHeaders != null && !finalHeaders.isEmpty()) {
                gen.writeStringProperty("headers", MaskingUtils.toMaskedString(maskedHeaders, mask, finalHeaders));
            }
            if (finalBody != null) {
                gen.writeStringProperty("body", finalBody);
            }
            gen.writeEndObject();
        };
        responseLog.atLevel(level)
            .addKeyValue("httpResponse", arg)
            .addKeyValue("clientConfigPath", clientConfigPath)
            .log("HttpClient response received");
    }

    public void logError(HttpClientRequest rq, long processingTime, Throwable exception) {
        if (!responseLog.isWarnEnabled()) {
            return;
        }
        var operation = getOperation(requestLog, rq.method(), rq.uri().getPath(), rq.uriTemplate());
        var exceptionTypeString = exception.getClass().getCanonicalName();
        var arg = (StructuredArgumentWriter) gen -> {
            gen.writeStartObject();
            gen.writeStringProperty("authority", rq.uri().getAuthority());
            gen.writeStringProperty("operation", operation);
            gen.writeStringProperty("resultCode", HttpResultCode.CONNECTION_ERROR.string());
            gen.writeNumberProperty("processingTime", processingTime / 1_000_000);
            if (exceptionTypeString != null) {
                gen.writeStringProperty("exceptionType", exceptionTypeString);
            }
            gen.writeEndObject();
        };
        requestLog.atWarn()
            .addKeyValue("httpResponse", arg)
            .addKeyValue("clientConfigPath", clientConfigPath)
            .log("HttpClient received error");
    }

    protected boolean shouldWritePath(Logger logger) {
        return pathTemplate != null ? pathTemplate : logger.isTraceEnabled();
    }

    protected String getOperation(Logger logger, String method, String path, String pathTemplate) {
        return method + ' ' + (shouldWritePath(logger) ? path : pathTemplate);
    }
}
