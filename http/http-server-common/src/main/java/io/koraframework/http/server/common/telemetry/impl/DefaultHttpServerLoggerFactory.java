package io.koraframework.http.server.common.telemetry.impl;

import io.koraframework.http.common.HttpResultCode;
import io.koraframework.http.common.header.HttpHeaders;
import io.koraframework.http.server.common.telemetry.impl.NoopHttpServerLoggerFactory.*;
import io.koraframework.logging.common.masking.MaskingStrategy;
import io.koraframework.http.common.telemetry.MaskingUtils;
import io.koraframework.http.server.common.HttpServer;
import io.koraframework.http.server.common.request.HttpServerRequest;
import io.koraframework.http.server.common.response.HttpServerResponse;
import io.koraframework.logging.common.arg.StructuredArgumentWriter;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class DefaultHttpServerLoggerFactory {

    public static final DefaultHttpServerLoggerFactory INSTANCE = new DefaultHttpServerLoggerFactory(new NoopMaskingStrategy());

    private final MaskingStrategy maskingStrategy;

    public DefaultHttpServerLoggerFactory(MaskingStrategy maskingStrategy) {
        this.maskingStrategy = maskingStrategy;
    }

    public DefaultHttpServerLogger create(DefaultHttpServerTelemetry.TelemetryContext context) {
        var requestLog = LoggerFactory.getLogger(HttpServer.class.getCanonicalName() + ".request");
        var responseLog = LoggerFactory.getLogger(HttpServer.class.getCanonicalName() + ".response");
        Set<String> rawQueries = context.config().logging().maskQueries();
        Set<String> maskedQueryParams = new HashSet<>(rawQueries.size());
        for (String e : rawQueries) {
            maskedQueryParams.add(e.toLowerCase(Locale.ROOT));
        }
        Set<String> rawHeaders = context.config().logging().maskHeaders();
        Set<String> maskedHeaders = new HashSet<>(rawHeaders.size());
        for (String e : rawHeaders) {
            maskedHeaders.add(e.toLowerCase(Locale.ROOT));
        }
        return new DefaultHttpServerLogger(requestLog, responseLog, maskedQueryParams, maskedHeaders, this.maskingStrategy, context);
    }

    public static class DefaultHttpServerLogger {

        protected final Logger requestLog;
        protected final Logger responseLog;
        protected final DefaultHttpServerTelemetry.TelemetryContext context;
        protected final Set<String> maskedQueryParams;
        protected final Set<String> maskedHeaders;
        protected final MaskingStrategy maskingStrategy;

        public DefaultHttpServerLogger(Logger requestLog,
                                       Logger responseLog,
                                       Set<String> maskedQueryParams,
                                       Set<String> maskedHeaders,
                                       MaskingStrategy maskingStrategy,
                                       DefaultHttpServerTelemetry.TelemetryContext context) {
            this.requestLog = requestLog;
            this.responseLog = responseLog;
            this.context = context;
            this.maskedQueryParams = maskedQueryParams;
            this.maskedHeaders = maskedHeaders;
            this.maskingStrategy = maskingStrategy;
        }

        public boolean logRequestBody() {
            return this.requestLog.isTraceEnabled();
        }

        public boolean logResponseBody() {
            return this.responseLog.isTraceEnabled();
        }

        private static final class RequestArgumentWriter implements StructuredArgumentWriter {
            private final DefaultHttpServerTelemetry.TelemetryContext context;
            private final String operation;
            private final Map<String, List<String>> finalQuery;
            private final Set<String> maskedQueryParams;
            private final MaskingStrategy maskingStrategy;
            private final HttpHeaders finalHeaders;
            private final Set<String> maskedHeaders;
            private final String finalBody;
            private final HttpServerRequest request;

            public RequestArgumentWriter(DefaultHttpServerTelemetry.TelemetryContext context,
                                         String operation,
                                         Map<String, List<String>> finalQuery,
                                         Set<String> maskedQueryParams,
                                         MaskingStrategy maskingStrategy,
                                         HttpHeaders finalHeaders,
                                         Set<String> maskedHeaders,
                                         String finalBody,
                                         HttpServerRequest request) {
                this.context = context;
                this.operation = operation;
                this.finalQuery = finalQuery;
                this.maskedQueryParams = maskedQueryParams;
                this.maskingStrategy = maskingStrategy;
                this.finalHeaders = finalHeaders;
                this.maskedHeaders = maskedHeaders;
                this.finalBody = finalBody;
                this.request = request;
            }

            @Override
            public void writeTo(tools.jackson.core.JsonGenerator gen) {
                gen.writeStartObject();
                gen.writeStringProperty("serverName", this.context.name());
                gen.writeNumberProperty("serverPort", this.context.port());
                gen.writeStringProperty("authority", request.host());
                gen.writeStringProperty("operation", operation);
                if (finalQuery != null && !finalQuery.isEmpty()) {
                    gen.writeStringProperty("queryParams", MaskingUtils.toMaskedString(maskedQueryParams, maskingStrategy, finalQuery));
                }
                if (finalHeaders != null && !finalHeaders.isEmpty()) {
                    gen.writeStringProperty("headers", MaskingUtils.toMaskedString(maskedHeaders, maskingStrategy, finalHeaders));
                }
                if (finalBody != null) {
                    gen.writeStringProperty("body", finalBody);
                }
                gen.writeEndObject();
            }
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
                ? this.context.bodyLogger().convertRequestBody(request, body, contentType)
                : null;
            var arg = new RequestArgumentWriter(context, operation, finalQuery, maskedQueryParams, maskingStrategy, finalHeaders, maskedHeaders, finalBody, request);

            requestLog.atLevel(level)
                .addKeyValue("httpRequest", arg)
                .log("HttpServer received request");
        }

        public void logStart(HttpServerRequest request) {
            this.logRequest(request, null, request.body().contentType());
        }

        private static final class ResponseArgumentWriter implements StructuredArgumentWriter {
            private final DefaultHttpServerTelemetry.TelemetryContext context;
            private final String operation;
            private final HttpResultCode resultCode;
            private final long processingTime;
            private final int statusCode;
            private final HttpHeaders finalHeaders;
            private final Set<String> maskedHeaders;
            private final MaskingStrategy maskingStrategy;
            private final Throwable exception;
            private final String finalBody;
            private final HttpServerRequest request;

            public ResponseArgumentWriter(DefaultHttpServerTelemetry.TelemetryContext context,
                                          String operation,
                                          HttpResultCode resultCode,
                                          long processingTime,
                                          int statusCode,
                                          HttpHeaders finalHeaders,
                                          Set<String> maskedHeaders,
                                          MaskingStrategy maskingStrategy,
                                          Throwable exception,
                                          String finalBody,
                                          HttpServerRequest request) {
                this.context = context;
                this.operation = operation;
                this.resultCode = resultCode;
                this.processingTime = processingTime;
                this.statusCode = statusCode;
                this.finalHeaders = finalHeaders;
                this.maskedHeaders = maskedHeaders;
                this.maskingStrategy = maskingStrategy;
                this.exception = exception;
                this.finalBody = finalBody;
                this.request = request;
            }

            @Override
            public void writeTo(tools.jackson.core.JsonGenerator gen) {
                gen.writeStartObject();
                gen.writeStringProperty("serverName", this.context.name());
                gen.writeNumberProperty("serverPort", this.context.port());
                gen.writeStringProperty("authority", request.host());
                gen.writeStringProperty("operation", operation);
                gen.writeStringProperty("resultCode", resultCode.string());
                gen.writeNumberProperty("processingTime", processingTime / 1_000_000);
                gen.writeNumberProperty("statusCode", statusCode);
                if (finalHeaders != null && !finalHeaders.isEmpty()) {
                    gen.writeStringProperty("headers", MaskingUtils.toMaskedString(maskedHeaders, maskingStrategy, finalHeaders));
                }
                if (exception != null) {
                    var exceptionType = exception.getClass().getCanonicalName();
                    gen.writeStringProperty("exceptionType", exceptionType);
                }
                if (finalBody != null) {
                    gen.writeStringProperty("body", finalBody);
                }
                gen.writeEndObject();
            }
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
                ? this.context.bodyLogger().convertResponseBody(request, response, body, contentType)
                : null;
            var finalHeaders = headers;
            var operation = getOperation(responseLog, request.method(), request.path(), request.pathTemplate());
            var statusCode = response.code();

            var w = new ResponseArgumentWriter(context, operation, resultCode, processingTime, statusCode, finalHeaders, maskedHeaders, maskingStrategy, exception, finalBody, request);
            final LoggingEventBuilder b;
            if (exception != null) {
                b = responseLog.atWarn();
                if (context.config().logging().stacktrace()) {
                    b.addArgument(exception);
                }
            } else if (responseLog.isDebugEnabled()) {
                b = responseLog.atDebug();
            } else {
                b = responseLog.atInfo();
            }
            b.addKeyValue("httpResponse", w);
            if (exception != null && context.config().logging().stacktrace()) {
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
            this.logResponse(request, HttpServerResponse.of(statusCode, headers), resultCode, processingTime, null, null, exception);
        }

        protected boolean shouldWritePathFull(Logger logger) {
            var pathFull = context.config().logging().pathFull();
            return pathFull != null ? pathFull : logger.isTraceEnabled();
        }

        protected String getOperation(Logger logger, String method, String path, @Nullable String pathTemplate) {
            if (shouldWritePathFull(logger)) {
                return method + ' ' + path;
            } else if (pathTemplate != null) {
                return method + ' ' + pathTemplate;
            } else {
                return method;
            }
        }
    }
}
