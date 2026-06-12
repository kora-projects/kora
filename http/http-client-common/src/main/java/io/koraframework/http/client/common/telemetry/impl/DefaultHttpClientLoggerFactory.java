package io.koraframework.http.client.common.telemetry.impl;

import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.client.common.response.HttpClientResponse;
import io.koraframework.http.common.HttpResultCode;
import io.koraframework.http.common.telemetry.MaskingUtils;
import io.koraframework.logging.common.arg.StructuredArgumentWriter;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultHttpClientLoggerFactory {

    public static final DefaultHttpClientLoggerFactory INSTANCE = new DefaultHttpClientLoggerFactory();

    public DefaultHttpClientLogger create(DefaultHttpClientTelemetry.TelemetryContext context) {
        var requestLog = LoggerFactory.getLogger(context.clientCanonicalName() + ".request");
        var responseLog = LoggerFactory.getLogger(context.clientCanonicalName() + ".response");
        var maskedQueryParams = context.config().logging().maskQueries().stream()
            .map(e -> e.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
        var maskedHeaders = context.config().logging().maskHeaders().stream()
            .map(e -> e.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
        return new DefaultHttpClientLogger(requestLog, responseLog, maskedQueryParams, maskedHeaders, context);
    }

    public static class DefaultHttpClientLogger {

        protected final Logger requestLog;
        protected final Logger responseLog;
        protected final DefaultHttpClientTelemetry.TelemetryContext context;
        protected final Set<String> maskedQueryParams;
        protected final Set<String> maskedHeaders;

        public DefaultHttpClientLogger(Logger requestLog,
                                       Logger responseLog,
                                       Set<String> maskedQueryParams,
                                       Set<String> maskedHeaders,
                                       DefaultHttpClientTelemetry.TelemetryContext context) {
            this.requestLog = requestLog;
            this.responseLog = responseLog;
            this.context = context;
            this.maskedQueryParams = maskedQueryParams;
            this.maskedHeaders = maskedHeaders;
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
                ? this.context.bodyLogger().convertRequestBody(this.context.clientConfigPath(), this.context.clientCanonicalName(), rq, body, contentType)
                : null;
            var arg = (StructuredArgumentWriter) gen -> {
                gen.writeStartObject();
                gen.writeStringProperty("authority", rq.uri().getAuthority());
                gen.writeStringProperty("operation", operation);
                if (finalQuery != null && !finalQuery.isEmpty()) {
                    gen.writeStringProperty("queryParams", MaskingUtils.toMaskedString(maskedQueryParams, context.config().logging().mask(), finalQuery));
                }
                if (finalHeaders != null && !finalHeaders.isEmpty()) {
                    gen.writeStringProperty("headers", MaskingUtils.toMaskedString(maskedHeaders, context.config().logging().mask(), finalHeaders));
                }
                if (finalBody != null) {
                    gen.writeStringProperty("body", finalBody);
                }
                gen.writeEndObject();
            };

            requestLog.atLevel(level)
                .addKeyValue("httpRequest", arg)
                .addKeyValue("clientConfigPath", this.context.clientConfigPath())
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
                ? this.context.bodyLogger().convertResponseBody(this.context.clientConfigPath(), this.context.clientCanonicalName(), rs, body, contentType)
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
                    gen.writeStringProperty("headers", MaskingUtils.toMaskedString(maskedHeaders, context.config().logging().mask(), finalHeaders));
                }
                if (finalBody != null) {
                    gen.writeStringProperty("body", finalBody);
                }
                gen.writeEndObject();
            };
            responseLog.atLevel(level)
                .addKeyValue("httpResponse", arg)
                .addKeyValue("clientConfigPath", this.context.clientConfigPath())
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
                .addKeyValue("clientConfigPath", this.context.clientConfigPath())
                .log("HttpClient error received");
        }

        protected boolean shouldWritePathFull(Logger logger) {
            var pathFull = context.config().logging().pathFull();
            return pathFull != null ? pathFull : logger.isTraceEnabled();
        }

        protected String getOperation(Logger logger, String method, String path, String pathTemplate) {
            return method + ' ' + (shouldWritePathFull(logger) ? path : pathTemplate);
        }
    }
}
