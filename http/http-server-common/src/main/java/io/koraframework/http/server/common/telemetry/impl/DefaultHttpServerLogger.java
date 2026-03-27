package io.koraframework.http.server.common.telemetry.impl;

import io.koraframework.http.common.HttpResultCode;
import io.koraframework.http.common.header.HttpHeaders;
import io.koraframework.http.common.telemetry.MaskingUtils;
import io.koraframework.http.server.common.HttpServer;
import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.telemetry.HttpServerTelemetryConfig;
import io.koraframework.logging.common.arg.StructuredArgumentWriter;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultHttpServerLogger {

    private static final Logger logger = LoggerFactory.getLogger(HttpServer.class);

    private final boolean logStacktrace;
    private final Set<String> maskedQueryParams;
    private final Set<String> maskedHeaders;
    private final String mask;
    private final Boolean pathTemplate;

    public DefaultHttpServerLogger(HttpServerTelemetryConfig.HttpServerLoggingConfig config) {
        this.logStacktrace = config.stacktrace();
        this.maskedQueryParams = config.maskQueries().stream()
            .map(e -> e.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
        this.maskedHeaders = config.maskHeaders().stream()
            .map(e -> e.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
        this.mask = config.mask();
        this.pathTemplate = config.pathTemplate();
    }

    public boolean isEnabled() {
        return logger.isWarnEnabled();
    }

    public void logStart(HttpServerRequest request) {
        if (!logger.isInfoEnabled()) {
            return;
        }
        var queryParams = request.queryParams();
        var headers = request.headers();
        if (!logger.isDebugEnabled()) {
            queryParams = null;
            headers = null;
        }
        var finalQuery = queryParams;
        var finalHeaders = headers;
        var operation = getOperation(request.method(), request.path(), request.pathTemplate());
        var arg = (StructuredArgumentWriter) gen -> {
            gen.writeStartObject();
            gen.writeStringProperty("operation", operation);
            if (finalQuery != null && !finalQuery.isEmpty()) {
                gen.writeStringProperty("queryParams", MaskingUtils.toMaskedString(maskedQueryParams, mask, finalQuery));
            }
            if (finalHeaders != null && !finalHeaders.isEmpty()) {
                gen.writeStringProperty("headers", MaskingUtils.toMaskedString(maskedHeaders, mask, finalHeaders));
            }
            gen.writeEndObject();
        };
        logger.atLevel(logger.isDebugEnabled() ? Level.DEBUG : Level.INFO)
            .addKeyValue("httpRequest", arg)
            .log("HttpServer received request");
    }

    public void logEnd(HttpServerRequest request, int statusCode, HttpResultCode resultCode, long processingTime, @Nullable HttpHeaders headers, @Nullable Throwable exception) {
        if (!logger.isWarnEnabled()) {
            return;
        }
        if (!logger.isDebugEnabled()) {
            headers = null;
        }
        var finalHeaders = headers;
        var operation = getOperation(request.method(), request.path(), request.pathTemplate());

        var w = (StructuredArgumentWriter) gen -> {
            gen.writeStartObject();
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
            gen.writeEndObject();
        };
        final LoggingEventBuilder b;
        if (exception != null) {
            b = logger.atWarn();
            if (logStacktrace) {
                b.addArgument(exception);
            }
        } else if (logger.isDebugEnabled()) {
            b = logger.atDebug();
        } else {
            b = logger.atInfo();
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

    private boolean shouldWritePath() {
        return pathTemplate != null ? !pathTemplate : logger.isTraceEnabled();
    }

    private String getOperation(String method, String path, String pathTemplate) {
        return method + ' ' + (shouldWritePath() ? path : pathTemplate);
    }
}
