package io.koraframework.http.server.common.telemetry.impl;

import io.koraframework.http.common.HttpResultCode;
import io.koraframework.http.common.header.HttpHeaders;
import io.koraframework.http.common.telemetry.MaskingUtils;
import io.koraframework.http.server.common.HttpServer;
import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.response.HttpServerResponse;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetryConfig;
import io.koraframework.logging.common.arg.StructuredArgumentWriter;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultHttpServerLogger {

    protected final HttpServerTelemetryConfig.HttpServerLoggingConfig config;
    protected final Logger requestLog;
    protected final Logger responseLog;
    protected final DefaultHttpServerBodyConverter bodyLogger;
    protected final boolean logStacktrace;
    protected final Set<String> maskedQueryParams;
    protected final Set<String> maskedHeaders;
    protected final String mask;
    @Nullable
    protected final Boolean pathTemplate;

    public DefaultHttpServerLogger(HttpServerTelemetryConfig.HttpServerLoggingConfig config) {
        this(
            LoggerFactory.getLogger(HttpServer.class.getCanonicalName() + ".request"),
            LoggerFactory.getLogger(HttpServer.class.getCanonicalName() + ".response"),
            new DefaultHttpServerBodyConverter(),
            config
        );
    }

    public DefaultHttpServerLogger(Logger requestLog,
                                   Logger responseLog,
                                   DefaultHttpServerBodyConverter bodyLogger,
                                   HttpServerTelemetryConfig.HttpServerLoggingConfig config) {
        this.requestLog = requestLog;
        this.responseLog = responseLog;
        this.bodyLogger = bodyLogger;
        this.logStacktrace = config.stacktrace();
        this.maskedQueryParams = config.maskQueries().stream()
            .map(e -> e.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
        this.maskedHeaders = config.maskHeaders().stream()
            .map(e -> e.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
        this.mask = config.mask();
        this.pathTemplate = config.pathTemplate();
        this.config = config;
    }

    public boolean logRequestBody() {
        return this.requestLog.isTraceEnabled();
    }

    public boolean logResponseBody() {
        return this.responseLog.isTraceEnabled();
    }

    public void logRequest(HttpServerRequest request,
                           @Nullable ByteBuffer body,
                           @Nullable String contentType) {
        if (!requestLog.isInfoEnabled()) {
            return;
        }

        var level = Level.INFO;
        var queryParams = request.queryParams();
        var headers = request.headers();
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
        var operation = getOperation(requestLog, request.method(), request.path(), request.pathTemplate());
        var finalBody = (body != null && body.remaining() > 0)
            ? bodyLogger.convertRequestBody(request, body, contentType)
            : null;
        var arg = (StructuredArgumentWriter) gen -> {
            gen.writeStartObject();
            gen.writeStringProperty("authority", request.host());
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
            .log("HttpServer received request");
    }

    public void logStart(HttpServerRequest request) {
        this.logRequest(request, null, request.body().contentType());
    }

    public void logResponse(HttpServerRequest request,
                            HttpServerResponse response,
                            HttpResultCode resultCode,
                            long processingTime,
                            @Nullable ByteBuffer body,
                            @Nullable String contentType,
                            @Nullable Throwable exception) {
        if (!responseLog.isWarnEnabled()) {
            return;
        }

        var headers = response.headers();
        if (!responseLog.isDebugEnabled()) {
            headers = null;
        }
        if (!responseLog.isTraceEnabled()) {
            body = null;
        }
        var finalBody = (body != null && body.remaining() > 0)
            ? bodyLogger.convertResponseBody(request, response, body, contentType)
            : null;
        var finalHeaders = headers;
        var operation = getOperation(responseLog, request.method(), request.path(), request.pathTemplate());
        var statusCode = response.code();

        var w = (StructuredArgumentWriter) gen -> {
            gen.writeStartObject();
            gen.writeStringProperty("authority", request.host());
            gen.writeStringProperty("operation", operation);
            gen.writeStringProperty("resultCode", resultCode.string());
            gen.writeNumberProperty("processingTime", processingTime / 1_000_000);
            gen.writeNumberProperty("statusCode", statusCode);
            if (finalHeaders != null && !finalHeaders.isEmpty()) {
                gen.writeStringProperty("headers", MaskingUtils.toMaskedString(maskedHeaders, mask, finalHeaders));
            }
            if (exception != null) {
                var exceptionType = exception.getClass().getCanonicalName();
                gen.writeStringProperty("exceptionType", exceptionType);
            }
            if (finalBody != null) {
                gen.writeStringProperty("body", finalBody);
            }
            gen.writeEndObject();
        };
        final LoggingEventBuilder b;
        if (exception != null) {
            b = responseLog.atWarn();
            if (logStacktrace) {
                b.addArgument(exception);
            }
        } else if (responseLog.isDebugEnabled()) {
            b = responseLog.atDebug();
        } else {
            b = responseLog.atInfo();
        }
        b.addKeyValue("httpResponse", w);
        if (exception != null && logStacktrace) {
            b.log("HttpServer responded error", exception);
        } else if (exception != null) {
            b.log("HttpServer responded error due to: {}", exception.getMessage());
        } else {
            b.log("HttpServer responded");
        }
    }

    public void logEnd(HttpServerRequest request,
                       int statusCode,
                       HttpResultCode resultCode,
                       long processingTime,
                       @Nullable HttpHeaders headers,
                       @Nullable Throwable exception) {
        this.logResponse(request, HttpServerResponse.of(statusCode, headers, null), resultCode, processingTime, null, null, exception);
    }

    protected boolean shouldWritePath(Logger logger) {
        return pathTemplate != null ? pathTemplate : logger.isTraceEnabled();
    }

    protected String getOperation(Logger logger, String method, String path, @Nullable String pathTemplate) {
        if (shouldWritePath(logger)) {
            return method + ' ' + path;
        } else if (pathTemplate != null) {
            return method + ' ' + pathTemplate;
        } else {
            return method;
        }
    }
}
