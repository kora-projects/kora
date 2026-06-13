package io.koraframework.bpmn.operaton.rest.telemetry.impl;

import io.koraframework.http.common.HttpResultCode;
import io.koraframework.http.common.header.HttpHeaders;
import io.koraframework.http.common.telemetry.MaskingUtils;
import io.koraframework.http.server.common.HttpServer;
import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.logging.common.arg.StructuredArgumentWriter;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultOperatonRestLoggerFactory {

    public static final DefaultOperatonRestLoggerFactory INSTANCE = new DefaultOperatonRestLoggerFactory();

    public DefaultOperatonRestLogger create(DefaultOperatonRestTelemetry.TelemetryContext context) {
        var logger = LoggerFactory.getLogger(HttpServer.class);
        var maskedQueryParams = context.config().logging().maskQueries().stream()
            .map(e -> e.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
        var maskedHeaders = context.config().logging().maskHeaders().stream()
            .map(e -> e.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
        return new DefaultOperatonRestLogger(context, logger, maskedQueryParams, maskedHeaders);
    }

    public static class DefaultOperatonRestLogger {

        protected final DefaultOperatonRestTelemetry.TelemetryContext context;
        protected final Logger logger;
        protected final Set<String> maskedQueryParams;
        protected final Set<String> maskedHeaders;

        public DefaultOperatonRestLogger(DefaultOperatonRestTelemetry.TelemetryContext context,
                                        Logger logger,
                                        Set<String> maskedQueryParams,
                                        Set<String> maskedHeaders) {
            this.context = context;
            this.logger = logger;
            this.maskedQueryParams = maskedQueryParams;
            this.maskedHeaders = maskedHeaders;
        }

        public void logStart(HttpServerRequest request) {
            if (!logger.isInfoEnabled()) {
                return;
            }

            var queryParams = request.queryParams();
            var headers = request.headers();
            var level = Level.INFO;
            if (!logger.isDebugEnabled()) {
                queryParams = null;
                headers = null;
            } else {
                level = Level.DEBUG;
            }

            var finalQuery = queryParams;
            var finalHeaders = headers;
            var operation = getOperation(request.method(), request.path(), request.pathTemplate());
            var arg = (StructuredArgumentWriter) gen -> {
                gen.writeStartObject();
                gen.writeStringProperty("operation", operation);
                if (finalQuery != null && !finalQuery.isEmpty()) {
                    gen.writeStringProperty("queryParams", MaskingUtils.toMaskedString(maskedQueryParams, context.config().logging().mask(), finalQuery));
                }
                if (finalHeaders != null && !finalHeaders.isEmpty()) {
                    gen.writeStringProperty("headers", MaskingUtils.toMaskedString(maskedHeaders, context.config().logging().mask(), finalHeaders));
                }
                gen.writeEndObject();
            };
            logger.atLevel(level)
                .addKeyValue("httpRequest", arg)
                .log("OperatonRest received request");
        }

        public void logEnd(HttpServerRequest request,
                           int statusCode,
                           HttpResultCode resultCode,
                           long processingTime,
                           @Nullable HttpHeaders headers,
                           @Nullable Throwable exception) {
            if (exception == null && !logger.isInfoEnabled()) {
                return;
            }
            if (exception != null && !logger.isWarnEnabled()) {
                return;
            }
            if (!logger.isDebugEnabled()) {
                headers = null;
            }

            var finalHeaders = headers;
            var operation = getOperation(request.method(), request.path(), request.pathTemplate());
            var arg = (StructuredArgumentWriter) gen -> {
                gen.writeStartObject();
                gen.writeStringProperty("operation", operation);
                gen.writeStringProperty("resultCode", resultCode.string());
                gen.writeNumberProperty("processingTime", processingTime / 1_000_000);
                gen.writeNumberProperty("statusCode", statusCode);
                if (finalHeaders != null && !finalHeaders.isEmpty()) {
                    gen.writeStringProperty("headers", MaskingUtils.toMaskedString(maskedHeaders, context.config().logging().mask(), finalHeaders));
                }
                if (exception != null) {
                    var exceptionType = exception.getClass().getCanonicalName();
                    if (exceptionType != null) {
                        gen.writeStringProperty("exceptionType", exceptionType);
                    }
                    if (!context.config().logging().stacktrace()) {
                        gen.writeStringProperty("exceptionMessage", exception.getMessage());
                    }
                }
                gen.writeEndObject();
            };

            if (exception != null) {
                logger.atWarn()
                    .addKeyValue("httpResponse", arg)
                    .setCause(context.config().logging().stacktrace() ? exception : null)
                    .log("OperatonRest errored response");
            } else {
                logger.atLevel(logger.isDebugEnabled() ? Level.DEBUG : Level.INFO)
                    .addKeyValue("httpResponse", arg)
                    .log("OperatonRest succeed response");
            }
        }

        protected boolean shouldWritePathFull() {
            var pathFull = context.config().logging().pathFull();
            return pathFull != null ? pathFull : logger.isTraceEnabled();
        }

        protected String getOperation(String method, String path, @Nullable String pathTemplate) {
            if (shouldWritePathFull()) {
                return method + ' ' + path;
            } else if (pathTemplate != null) {
                return method + ' ' + pathTemplate;
            } else {
                return method;
            }
        }
    }
}
