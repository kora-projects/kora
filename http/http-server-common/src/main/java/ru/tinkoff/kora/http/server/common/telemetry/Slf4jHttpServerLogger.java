package ru.tinkoff.kora.http.server.common.telemetry;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.server.common.HttpServer;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Slf4jHttpServerLogger implements HttpServerLogger {

    private static final int AVERAGE_HEADER_SIZE = 15;

    private static final Logger log = LoggerFactory.getLogger(HttpServer.class);
    private final boolean logStacktrace;
    private final Set<String> maskedQueryParams;
    private final Set<String> maskedHeaders;
    private final String maskFiller;
    private final Boolean pathTemplate;

    public Slf4jHttpServerLogger(boolean stacktrace,
                                 Set<String> maskedQueryParams,
                                 Set<String> maskedHeaders,
                                 String maskFiller,
                                 Boolean pathTemplate) {
        this.logStacktrace = stacktrace;
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
    public boolean isEnabled() {
        return log.isInfoEnabled();
    }

    @Override
    public void logStart(String method,
                         String path,
                         String pathTemplate,
                         Map<String, ? extends Collection<String>> queryParams,
                         @Nullable HttpHeaders headers) {
        if (!log.isInfoEnabled()) {
            return;
        }

        final String operation = getOperation(method, path, pathTemplate);

        var marker = StructuredArgument.marker("httpRequest", gen -> {
            gen.writeStartObject();
            gen.writeStringField("operation", operation);
            gen.writeEndObject();
        });

        if (log.isDebugEnabled()) {
            var headersString = requestHeaderString(headers);
            var queryParamsString = requestQueryParamsString(queryParams);
            log.debug(marker, "HttpServer received request for {}{}\n{}", operation, queryParamsString, headersString);
        } else {
            log.info(marker, "HttpServer received request for {}", operation);
        }
    }

    @Override
    public void logEnd(String method,
                       String path,
                       String pathTemplate,
                       Integer statusCode,
                       HttpResultCode resultCode,
                       long processingTime,
                       Map<String, ? extends Collection<String>> queryParams,
                       @Nullable HttpHeaders headers,
                       @Nullable Throwable exception) {
        if (!log.isWarnEnabled()) {
            return;
        }

        final String operation = getOperation(method, path, pathTemplate);

        var marker = StructuredArgument.marker("httpResponse", gen -> {
            gen.writeStartObject();
            gen.writeStringField("operation", operation);
            gen.writeStringField("resultCode", resultCode.name().toLowerCase());
            gen.writeNumberField("processingTime", processingTime / 1_000_000);
            gen.writeNumberField("statusCode", statusCode);
            if (exception != null) {
                var exceptionType = exception.getClass().getCanonicalName();
                gen.writeStringField("exceptionType", exceptionType);
            }
            gen.writeEndObject();
        });

        if (log.isDebugEnabled()) {
            var headersString = requestHeaderString(headers);
            var queryParamsString = requestQueryParamsString(queryParams);
            if (exception != null) {
                if (this.logStacktrace) {
                    log.warn(marker, "HttpServer responded error {} for {}{}\n{}", statusCode, operation, queryParamsString, headersString, exception);
                } else {
                    log.warn(marker, "HttpServer responded error {} for {}{} due to: {} \n{}", statusCode, operation, queryParamsString, exception.getMessage(), headersString);
                }
            } else {
                log.debug(marker, "HttpServer responded {} for {}{}\n{}", statusCode, operation, queryParamsString, headersString);
            }
        } else if (statusCode != null) {
            if (exception != null) {
                if (this.logStacktrace) {
                    log.warn(marker, "HttpServer responded error {} for {}", statusCode, operation, exception);
                } else {
                    log.warn(marker, "HttpServer responded error {} for {} due to: {}", statusCode, operation, exception.getMessage());
                }
            } else {
                log.info(marker, "HttpServer responded {} for {}", statusCode, operation);
            }
        } else {
            if (exception != null) {
                if (this.logStacktrace) {
                    log.warn(marker, "HttpServer responded error for {}", operation, exception);
                } else {
                    log.warn(marker, "HttpServer responded error for {} due to: {}", operation, exception.getMessage());
                }
            } else {
                log.info(marker, "HttpServer responded for {}", operation);
            }
        }
    }

    protected String requestHeaderString(@Nullable HttpHeaders headers) {
        return toMaskedString(headers);
    }

    protected String requestQueryParamsString(Map<String, ? extends Collection<String>> queryParams) {
        final String result = toMaskedString(queryParams);
        return result.isEmpty() ? result : '?' + result;
    }

    private String toMaskedString(@Nullable HttpHeaders headers) {
        if (headers == null || headers.isEmpty()) {
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

    private String toMaskedString(Map<String, ? extends Collection<String>> queryParams) {
        if (queryParams.isEmpty()) {
            return "";
        }
        return queryParams.entrySet().stream()
            .map(e -> {
                final String key = e.getKey();
                final Collection<String> values = e.getValue();
                if (maskedQueryParams.contains(key.toLowerCase(Locale.ROOT))) {
                    return key + '=' + maskFiller;
                } else {
                    if (values.isEmpty()) {
                        return key + '=';
                    } else {
                        return values.stream().map(v -> key + '=' + v).collect(Collectors.joining("&"));
                    }
                }
            }).collect(Collectors.joining("&"));
    }

    private boolean shouldWritePath() {
        return pathTemplate != null ? !pathTemplate : log.isTraceEnabled();
    }

    private String getOperation(String method, String path, String pathTemplate) {
        return method + ' ' + (shouldWritePath() ? path : pathTemplate);
    }
}
