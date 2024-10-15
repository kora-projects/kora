package ru.tinkoff.kora.http.server.common.telemetry;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.server.common.HttpServer;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;

import java.util.Collection;
import java.util.Iterator;
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
    private final String mask;
    private final Boolean pathTemplate;

    public Slf4jHttpServerLogger(boolean stacktrace,
                                 Set<String> maskedQueryParams,
                                 Set<String> maskedHeaders,
                                 String mask,
                                 Boolean pathTemplate) {
        this.logStacktrace = stacktrace;
        this.maskedQueryParams = maskedQueryParams.stream()
            .map(e -> e.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
        this.maskedHeaders = maskedHeaders.stream()
            .map(e -> e.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
        this.mask = mask;
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
            logServerReceivedRequest(marker, Level.DEBUG, operation, queryParams, headers);
        } else {
            logServerReceivedRequest(marker, Level.INFO, operation, null, null);
        }
    }

    @Override
    public void logEnd(String method,
                       String path,
                       String pathTemplate,
                       int statusCode,
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

        if (exception != null) {
            logServerRespondedWithException(marker, statusCode, operation, queryParams, headers, exception);
        } else {
            if (log.isDebugEnabled()) {
                logServerResponded(marker, Level.DEBUG, statusCode, operation, queryParams, headers);
            } else {
                logServerResponded(marker, Level.INFO, statusCode, operation, null, null);
            }
        }
    }

    protected String requestHeaderString(HttpHeaders headers) {
        return toMaskedString(headers);
    }

    protected String requestQueryParamsString(Map<String, ? extends Collection<String>> queryParams) {
        return toMaskedString(queryParams);
    }

    private void logServerReceivedRequest(Marker marker, Level level, String operation,
                                          @Nullable Map<String, ? extends Collection<String>> queryParams,
                                          @Nullable HttpHeaders headers) {
        boolean shouldWriteQueryParams = queryParams != null && !queryParams.isEmpty();
        boolean shouldWriteHeaders = headers != null && !headers.isEmpty();
        if (shouldWriteQueryParams) {
            if (shouldWriteHeaders) {
                log.atLevel(level).addMarker(marker)
                    .log("HttpServer received request for {}?{}\n{}", operation, requestQueryParamsString(queryParams), requestHeaderString(headers));
            } else {
                log.atLevel(level).addMarker(marker)
                    .log("HttpServer received request for {}?{}", operation, requestQueryParamsString(queryParams));
            }
        } else {
            if (shouldWriteHeaders) {
                log.atLevel(level).addMarker(marker)
                    .log("HttpServer received request for {}\n{}", operation, requestHeaderString(headers));
            } else {
                log.atLevel(level).addMarker(marker)
                    .log("HttpServer received request for {}", operation);
            }
        }
    }

    private void logServerRespondedWithException(Marker marker, int statusCode, String operation,
                                                 @Nullable Map<String, ? extends Collection<String>> queryParams,
                                                 @Nullable HttpHeaders headers, Throwable exception) {
        boolean shouldWriteQueryParams = queryParams != null && !queryParams.isEmpty();
        boolean shouldWriteHeaders = headers != null && !headers.isEmpty();
        if (logStacktrace) {
            if (shouldWriteQueryParams) {
                if (shouldWriteHeaders) {
                    log.warn(marker, "HttpServer responded error {} for {}?{}\n{}", statusCode, operation, requestQueryParamsString(queryParams), requestHeaderString(headers), exception);
                } else {
                    log.warn(marker, "HttpServer responded error {} for {}?{}", statusCode, operation, requestQueryParamsString(queryParams), exception);
                }
            } else {
                if (shouldWriteHeaders) {
                    log.warn(marker, "HttpServer responded error {} for {}\n{}", statusCode, operation, requestHeaderString(headers), exception);
                } else {
                    log.warn(marker, "HttpServer responded error {} for {}", statusCode, operation, exception);
                }
            }
        } else {
            if (shouldWriteQueryParams) {
                if (shouldWriteHeaders) {
                    log.warn(marker, "HttpServer responded error {} for {}?{} due to: {}\n{}", statusCode, operation, requestQueryParamsString(queryParams), exception.getMessage(), requestHeaderString(headers));
                } else {
                    log.warn(marker, "HttpServer responded error {} for {}?{} due to: {}", statusCode, operation, requestQueryParamsString(queryParams), exception.getMessage());
                }
            } else {
                if (shouldWriteHeaders) {
                    log.warn(marker, "HttpServer responded error {} for {} due to: {}\n{}", statusCode, operation, exception.getMessage(), requestHeaderString(headers));
                } else {
                    log.warn(marker, "HttpServer responded error {} for {} due to: {}", statusCode, operation, exception.getMessage());
                }
            }
        }
    }

    private void logServerResponded(Marker marker, Level level, int statusCode, String operation,
                                    @Nullable Map<String, ? extends Collection<String>> queryParams,
                                    @Nullable HttpHeaders headers) {
        boolean shouldWriteQueryParams = queryParams != null && !queryParams.isEmpty();
        boolean shouldWriteHeaders = headers != null && !headers.isEmpty();
        if (shouldWriteQueryParams) {
            if (shouldWriteHeaders) {
                log.atLevel(level).addMarker(marker)
                    .log("HttpServer responded {} for {}?{}\n{}", statusCode, operation, requestQueryParamsString(queryParams), requestHeaderString(headers));
            } else {
                log.atLevel(level).addMarker(marker)
                    .log("HttpServer responded {} for {}?{}", statusCode, operation, requestQueryParamsString(queryParams));
            }
        } else {
            if (shouldWriteHeaders) {
                log.atLevel(level).addMarker(marker)
                    .log("HttpServer responded {} for {}\n{}", statusCode, operation, requestHeaderString(headers));
            } else {
                log.atLevel(level).addMarker(marker)
                    .log("HttpServer responded {} for {}", statusCode, operation);
            }
        }
    }

    private String toMaskedString(HttpHeaders headers) {
        var sb = new StringBuilder(headers.size() * AVERAGE_HEADER_SIZE);
        final Iterator<Map.Entry<String, List<String>>> iterator = headers.iterator();
        while (iterator.hasNext()) {
            final Map.Entry<String, List<String>> headerEntry = iterator.next();
            // В HttpHeaders все заголовки в нижнем регистре, приведение не требуется
            final String headerKey = headerEntry.getKey();
            final List<String> headerValues = headerEntry.getValue();
            sb.append(headerKey)
                .append(": ")
                .append(maskedHeaders.contains(headerKey) ? mask : String.join(", ", headerValues));
            if (iterator.hasNext()) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private String toMaskedString(Map<String, ? extends Collection<String>> queryParams) {
        return queryParams.entrySet().stream()
            .map(e -> {
                final String key = e.getKey();
                final Collection<String> values = e.getValue();
                if (maskedQueryParams.contains(key.toLowerCase(Locale.ROOT))) {
                    return key + '=' + mask;
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
