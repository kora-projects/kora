package io.koraframework.http.client.common.telemetry.impl;

import io.koraframework.http.client.common.request.HttpClientRequest;
import io.koraframework.http.client.common.response.HttpClientResponse;
import io.koraframework.http.client.common.telemetry.HttpClientTelemetryConfig;
import io.koraframework.http.common.HttpResultCode;
import io.koraframework.http.common.telemetry.MaskingUtils;
import io.koraframework.logging.common.arg.StructuredArgumentWriter;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.slf4j.helpers.NOPLogger;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultHttpClientLogger {

    protected final HttpClientTelemetryConfig.HttpClientLoggerConfig config;
    protected final String clientName;
    protected final String clientImpl;
    protected final Logger requestLog;
    protected final Logger responseLog;
    protected final Set<String> maskedQueryParams;
    protected final Set<String> maskedHeaders;
    protected final String mask;
    protected final boolean pathTemplate;

    public DefaultHttpClientLogger(String clientName,
                                   String clientImpl,
                                   HttpClientTelemetryConfig.HttpClientLoggerConfig config) {
        this.clientName = clientName;
        this.clientImpl = clientImpl;
        this.requestLog = config.enabled()
            ? LoggerFactory.getLogger(clientImpl + ".request")
            : NOPLogger.NOP_LOGGER;
        this.responseLog = config.enabled()
            ? LoggerFactory.getLogger(clientImpl + ".response")
            : NOPLogger.NOP_LOGGER;

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
            ? requestBodyString(body, contentType)
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
            .addKeyValue("clientName", clientName)
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
            ? responseBodyString(body, contentType)
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
            .addKeyValue("clientName", clientName)
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
            .addKeyValue("clientName", clientName)
            .log("HttpClient received error");
    }

    @Nullable
    protected String responseBodyString(ByteBuffer body, @Nullable String contentType) {
        var charset = detectCharset(contentType);
        if (charset == null) {
            return null;
        }

        return charset.decode(body).toString();
    }

    @Nullable
    protected String requestBodyString(ByteBuffer body, @Nullable String contentType) {
        var charset = detectCharset(contentType);
        if (charset == null) {
            return null;
        }

        return charset.decode(body).toString();
    }

    protected boolean shouldWritePath(Logger logger) {
        return !pathTemplate && logger.isTraceEnabled();
    }

    protected String getOperation(Logger logger, String method, String path, String pathTemplate) {
        return method + ' ' + (shouldWritePath(logger) ? path : pathTemplate);
    }

    @Nullable
    protected Charset detectCharset(String contentType) {
        if (contentType == null) {
            return null;
        }

        var split = contentType.split("; charset=", 2);
        if (split.length == 2) {
            return Charset.forName(split[1]);
        }

        var mimeType = split[0];
        if (mimeType.contains("text") || mimeType.contains("json") || mimeType.contains("xml")) {
            return StandardCharsets.UTF_8;
        }

        if (mimeType.contains("application/x-www-form-urlencoded")) {
            return StandardCharsets.US_ASCII;
        }
        return null;
    }
}
