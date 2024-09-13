package ru.tinkoff.kora.http.client.common.telemetry;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
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
    private final boolean alwaysWriteFullPath;

    public Sl4fjHttpClientLogger(Logger requestLog, Logger responseLog,
                                 Set<String> maskedQueryParams, Set<String> maskedHeaders,
                                 String maskFiller, boolean alwaysWriteFullPath) {
        this.requestLog = requestLog;
        this.responseLog = responseLog;
        this.maskedQueryParams = maskedQueryParams.stream()
            .map(e -> e.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
        this.maskedHeaders = maskedHeaders.stream()
            .map(e -> e.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
        this.maskFiller = maskFiller;
        this.alwaysWriteFullPath = alwaysWriteFullPath;
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
                           @Nullable String path,
                           @Nullable String pathTemplate,
                           @Nullable String resolvedUri,
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

        if (this.requestLog.isTraceEnabled() && headers != null && headers.size() > 0 && body != null) {
            var headersString = this.requestHeaderString(headers);
            var bodyStr = this.requestBodyString(body);
            var queryParamsString = this.requestQueryParamsString(queryParams);
            this.requestLog.trace(marker, "HttpClient requesting {}{}\n{}\n{}", operation, queryParamsString, headersString, bodyStr);
        } else if (this.requestLog.isDebugEnabled() && headers != null && headers.size() > 0) {
            var headersString = this.requestHeaderString(headers);
            var queryParamsString = this.requestQueryParamsString(queryParams);
            this.requestLog.debug(marker, "HttpClient requesting {}{}\n{}", operation, queryParamsString, headersString);
        } else {
            this.requestLog.info(marker, "HttpClient requesting {}", operation);
        }
    }

    @Override
    public void logResponse(String authority,
                            String method,
                            @Nullable String path,
                            @Nullable String pathTemplate,
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

        if (responseLog.isTraceEnabled() && headers != null && headers.size() > 0 && body != null) {
            var headersString = this.responseHeaderString(headers);
            var bodyStr = this.responseBodyString(body);
            responseLog.trace(marker, "HttpClient received {} from {}\n{}\n{}", statusCode, operation, headersString, bodyStr);
        } else if (responseLog.isDebugEnabled() && headers != null && headers.size() > 0) {
            var headersString = this.responseHeaderString(headers);
            responseLog.debug(marker, "HttpClient received {} from {}\n{}", statusCode, operation, headersString);
        } else if (statusCode != null) {
            responseLog.info(marker, "HttpClient received {} from {}", statusCode, operation);
        } else {
            responseLog.info(marker, "HttpClient received No HttpResponse from {}", operation);
        }
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
        return toMaskedString(headers);
    }

    public String requestQueryParamsString(@Nullable String queryParams) {
        final String result = queryParams != null ? toMaskedString(queryParams) : "";
        return result.isEmpty() ? result : '?' + result;
    }

    private String toMaskedString(HttpHeaders headers) {
        if (headers.isEmpty()) {
            return "";
        }
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
        if (queryParams.isEmpty()) {
            return "";
        }
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
        return alwaysWriteFullPath || logger.isTraceEnabled();
    }

    private String getOperation(Logger logger, String method, @Nullable String path, @Nullable String pathTemplate) {
        return method + ' ' + Objects.requireNonNullElse((shouldWritePath(logger) ? path : pathTemplate), "");
    }
}
