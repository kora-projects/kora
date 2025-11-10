package ru.tinkoff.kora.http.client.common.telemetry.impl;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.event.Level;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.http.client.common.telemetry.HttpClientTelemetryConfig;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.logging.common.arg.StructuredArgumentWriter;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultHttpClientLogger {

    private static final int AVERAGE_HEADER_SIZE = 15;

    private final Logger requestLog;
    private final Logger responseLog;
    private final Set<String> maskedQueryParams;
    private final Set<String> maskedHeaders;
    private final String mask;
    private final Boolean pathTemplate;

    public DefaultHttpClientLogger(Logger requestLog, Logger responseLog, HttpClientTelemetryConfig.HttpClientLoggerConfig config) {
        this.requestLog = requestLog;
        this.responseLog = responseLog;
        this.maskedQueryParams = config.maskQueries().stream()
            .map(e -> e.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
        this.maskedHeaders = config.maskHeaders().stream()
            .map(e -> e.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
        this.mask = config.mask();
        this.pathTemplate = config.pathTemplate();
    }

    public boolean logRequestBody() {
        return this.requestLog.isTraceEnabled();
    }

    public boolean logResponseBody() {
        return this.responseLog.isTraceEnabled();
    }

    public void logRequest(HttpClientRequest rq, @Nullable String body) {
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
        var finalBody = body;
        var arg = (StructuredArgumentWriter) gen -> {
            gen.writeStartObject();
            gen.writeStringField("authority", rq.uri().getAuthority());
            gen.writeStringField("operation", operation);
            if (finalQuery != null && !finalQuery.isEmpty()) {
                gen.writeStringField("queryParams", toMaskedString(finalQuery));
            }
            if (finalHeaders != null && !finalHeaders.isEmpty()) {
                gen.writeStringField("headers", toMaskedString(finalHeaders));
            }
            if (finalBody != null) {
                gen.writeStringField("body", requestBodyString(finalBody));
            }
            gen.writeEndObject();
        };

        requestLog.atLevel(level)
            .addKeyValue("httpRequest", arg)
            .log("HttpServer received request");
    }

    public void logResponse(HttpClientRequest rq, HttpClientResponse rs, long processingTime, @Nullable String body) {
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
        var finalBody = body;
        var statusCode = rs != null
            ? rs.code()
            : null;

        var arg = (StructuredArgumentWriter) gen -> {
            gen.writeStartObject();
            gen.writeStringField("authority", rq.uri().getAuthority());
            gen.writeStringField("operation", operation);
            gen.writeStringField("resultCode", HttpResultCode.fromStatusCode(rs.code()).string());
            gen.writeNumberField("processingTime", processingTime / 1_000_000);
            if (statusCode != null) {
                gen.writeNumberField("statusCode", statusCode);
            }
            if (finalHeaders != null && !finalHeaders.isEmpty()) {
                gen.writeStringField("headers", toMaskedString(finalHeaders));
            }
            if (finalBody != null) {
                gen.writeStringField("body", responseBodyString(finalBody));
            }
            gen.writeEndObject();
        };
        responseLog.atLevel(level)
            .addKeyValue("httpResponse", arg)
            .log("HttpClient received response");
    }

    public void logError(HttpClientRequest rq, long processingTime, Throwable exception) {
        if (!responseLog.isWarnEnabled()) {
            return;
        }
        var operation = getOperation(requestLog, rq.method(), rq.uri().getPath(), rq.uriTemplate());
        var exceptionTypeString = exception.getClass().getCanonicalName();
        var arg = (StructuredArgumentWriter) gen -> {
            gen.writeStartObject();
            gen.writeStringField("authority", rq.uri().getAuthority());
            gen.writeStringField("operation", operation);
            gen.writeStringField("resultCode", HttpResultCode.CONNECTION_ERROR.string());
            gen.writeNumberField("processingTime", processingTime / 1_000_000);
            if (exceptionTypeString != null) {
                gen.writeStringField("exceptionType", exceptionTypeString);
            }
            gen.writeEndObject();
        };
        requestLog.atWarn()
            .addKeyValue("httpResponse", arg)
            .log("HttpClient received error");
    }

    public String responseBodyString(String body) {
        return body;
    }

    public String requestBodyString(String body) {
        return body;
    }

    private String toMaskedString(HttpHeaders headers) {
        var sb = new StringBuilder(headers.size() * AVERAGE_HEADER_SIZE);
        var iterator = headers.iterator();
        while (iterator.hasNext()) {
            var headerEntry = iterator.next();
            // В HttpHeaders все заголовки в нижнем регистре, приведение не требуется
            var headerKey = headerEntry.getKey();
            var headerValues = headerEntry.getValue();
            sb.append(headerKey)
                .append(": ")
                .append(maskedHeaders.contains(headerKey) ? mask : String.join(", ", headerValues));
            if (iterator.hasNext()) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private String toMaskedString(String queryParams) {
        if (maskedQueryParams.isEmpty()) {
            return queryParams;
        }

        return Arrays.stream(queryParams.split("&"))
            .map(str -> {
                final int i = str.indexOf('=');
                if (i == -1) {
                    return str;
                }
                final String paramName = str.substring(0, i);
                if (maskedQueryParams.contains(paramName.toLowerCase(Locale.ROOT))) {
                    return paramName + '=' + mask;
                } else {
                    return str;
                }
            })
            .collect(Collectors.joining("&"));
    }

    private boolean shouldWritePath(Logger logger) {
        return pathTemplate != null ? !pathTemplate : logger.isTraceEnabled();
    }

    private String getOperation(Logger logger, String method, String path, String pathTemplate) {
        return method + ' ' + (shouldWritePath(logger) ? path : pathTemplate);
    }
}
