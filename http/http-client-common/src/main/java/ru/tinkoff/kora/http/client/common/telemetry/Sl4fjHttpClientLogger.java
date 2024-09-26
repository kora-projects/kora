package ru.tinkoff.kora.http.client.common.telemetry;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;

public class Sl4fjHttpClientLogger implements HttpClientLogger {

    private static final int AVERAGE_HEADER_SIZE = 15;

    private final Logger requestLog;
    private final Logger responseLog;
    private final Set<String> maskedQueryParams;
    private final Set<String> maskedHeaders;
    private final String maskFiller;
    private final Boolean pathTemplate;

    public Sl4fjHttpClientLogger(Logger requestLog, Logger responseLog,
                                 Set<String> maskedQueryParams, Set<String> maskedHeaders,
                                 String maskFiller, Boolean pathTemplate) {
        this.requestLog = requestLog;
        this.responseLog = responseLog;
        this.maskedQueryParams = maskedQueryParams.stream()
            .map(e -> e.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
        this.maskedHeaders = maskedHeaders.stream()
            .map(e -> e.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
        this.maskFiller = maskFiller;
        this.pathTemplate = pathTemplate;
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
                           String path,
                           String pathTemplate,
                           String resolvedUri,
                           @Nullable String queryParams,
                           @Nullable HttpHeaders headers,
                           @Nullable String body) {

        final String operation = getOperation(requestLog, method, path, pathTemplate);

        var marker = StructuredArgument.marker("httpRequest", gen -> {
            gen.writeStartObject();
            gen.writeStringField("authority", authority);
            gen.writeStringField("operation", operation);
            gen.writeEndObject();
        });

        if (requestLog.isTraceEnabled()) {
            logHttpRequest(marker, Level.TRACE, operation, queryParams, headers, body);
        } else if (requestLog.isDebugEnabled()) {
            logHttpRequest(marker, Level.DEBUG, operation, queryParams, headers, null);
        } else {
            logHttpRequest(marker, Level.INFO, operation, null, null, null);
        }
    }

    @Override
    public void logResponse(String authority,
                            String method,
                            String path,
                            String pathTemplate,
                            long processingTime,
                            @Nullable Integer statusCode,
                            HttpResultCode resultCode,
                            @Nullable Throwable exception,
                            @Nullable HttpHeaders headers,
                            @Nullable String body) {
        var exceptionTypeString = exception != null
            ? exception.getClass().getCanonicalName()
            : statusCode != null ? null : CancellationException.class.getCanonicalName();

        final String operation = getOperation(responseLog, method, path, pathTemplate);

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

        if (responseLog.isTraceEnabled()) {
            logHttpResponse(marker, Level.TRACE, statusCode, operation, headers, body);
        } else if (responseLog.isDebugEnabled()) {
            logHttpResponse(marker, Level.DEBUG, statusCode, operation, headers, null);
        } else if (statusCode != null) {
            logHttpResponse(marker, Level.INFO, statusCode, operation, null, null);
        } else {
            responseLog.info(marker, "HttpClient received No HttpResponse from {}", operation);
        }
    }

    public String responseBodyString(String body) {
        return body;
    }

    public String responseHeaderString(HttpHeaders headers) {
        return toMaskedString(headers);
    }

    public String requestBodyString(String body) {
        return body;
    }

    public String requestHeaderString(HttpHeaders headers) {
        return toMaskedString(headers);
    }

    public String requestQueryParamsString(String queryParams) {
        return toMaskedString(queryParams);
    }

    private void logHttpRequest(Marker marker, Level level, String operation,
                                @Nullable String queryParams, @Nullable HttpHeaders headers, @Nullable String body) {
        boolean shouldWriteQueryParams = queryParams != null && !queryParams.isEmpty();
        boolean shouldWriteHeaders = headers != null && !headers.isEmpty();
        boolean shouldWriteBody = body != null;
        requestLog.atLevel(level).addMarker(marker)
            .log("HttpClient requesting {}{}{}{}", operation,
                 shouldWriteQueryParams ? '?' + requestQueryParamsString(queryParams) : "",
                 shouldWriteHeaders ? '\n' + requestHeaderString(headers) : "",
                 shouldWriteBody ? '\n' + requestBodyString(body) : "");
    }

    private void logHttpResponse(Marker marker, Level level, Integer statusCode, String operation,
                                 @Nullable HttpHeaders headers, @Nullable String body) {
        boolean shouldWriteHeaders = headers != null && !headers.isEmpty();
        boolean shouldWriteBody = body != null;
        responseLog.atLevel(level).addMarker(marker)
            .log("HttpClient received {} from {}{}{}",
                 statusCode, operation,
                 shouldWriteHeaders ? '\n' + responseHeaderString(headers) : "",
                 shouldWriteBody ? '\n' + responseBodyString(body) : "");
    }

    private String toMaskedString(HttpHeaders headers) {
        var sb = new StringBuilder(headers.size() * AVERAGE_HEADER_SIZE);
        headers.forEach((headerEntry) -> {
            // В HttpHeaders все заголовки в нижнем регистре, приведение не требуется
            final String headerKey = headerEntry.getKey();
            final List<String> headerValues = headerEntry.getValue();
            sb.append(headerKey)
                .append(": ")
                .append(maskedHeaders.contains(headerKey) ? maskFiller : String.join(", ", headerValues))
                .append('\n');
        });
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
                    return paramName + '=' + maskFiller;
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
