package ru.tinkoff.kora.http.server.common.telemetry.impl;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.server.common.HttpServer;
import ru.tinkoff.kora.http.server.common.HttpServerRequest;
import ru.tinkoff.kora.http.server.common.telemetry.HttpServerTelemetryConfig;
import ru.tinkoff.kora.logging.common.arg.StructuredArgumentWriter;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class DefaultHttpServerLogger {

    private static final int AVERAGE_HEADER_SIZE = 15;

    private static final Logger log = LoggerFactory.getLogger(HttpServer.class);
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
        return log.isWarnEnabled();
    }

    public void logStart(HttpServerRequest request) {
        if (!log.isInfoEnabled()) {
            return;
        }
        var queryParams = request.queryParams();
        var headers = request.headers();
        if (!log.isDebugEnabled()) {
            queryParams = null;
            headers = null;
        }
        var finalQuery = queryParams;
        var finalHeaders = headers;
        var operation = getOperation(request.method(), request.path(), request.route());
        var arg = (StructuredArgumentWriter) gen -> {
            gen.writeStartObject();
            gen.writeStringField("operation", operation);
            if (finalQuery != null && !finalQuery.isEmpty()) {
                gen.writeStringField("queryParams", toMaskedString(finalQuery));
            }
            if (finalHeaders != null && !finalHeaders.isEmpty()) {
                gen.writeStringField("headers", toMaskedString(finalHeaders));
            }
            gen.writeEndObject();
        };
        log.atLevel(log.isDebugEnabled() ? Level.DEBUG : Level.INFO)
            .addKeyValue("httpRequest", arg)
            .log("HttpServer received request");
    }

    public void logEnd(HttpServerRequest request, int statusCode, HttpResultCode resultCode, long processingTime, @Nullable HttpHeaders headers, @Nullable Throwable exception) {
        if (!log.isWarnEnabled()) {
            return;
        }
        if (!log.isDebugEnabled()) {
            headers = null;
        }
        var finalHeaders = headers;
        var operation = getOperation(request.method(), request.path(), request.route());

        var w = (StructuredArgumentWriter) gen -> {
            gen.writeStartObject();
            gen.writeStringField("operation", operation);
            gen.writeStringField("resultCode", resultCode.string());
            gen.writeNumberField("processingTime", processingTime / 1_000_000);
            gen.writeNumberField("statusCode", statusCode);
            if (finalHeaders != null && !finalHeaders.isEmpty()) {
                gen.writeStringField("headers", toMaskedString(finalHeaders));
            }
            if (exception != null) {
                var exceptionType = exception.getClass().getCanonicalName();
                gen.writeStringField("exceptionType", exceptionType);
            }
            gen.writeEndObject();
        };
        final LoggingEventBuilder b;
        if (exception != null) {
            b = log.atWarn();
            if (logStacktrace) {
                b.addArgument(exception);
            }
        } else if (log.isDebugEnabled()) {
            b = log.atDebug();
        } else {
            b = log.atInfo();
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

    private String toMaskedString(Map<String, ? extends Collection<String>> queryParams) {
        var sb = new StringBuilder(queryParams.size() * AVERAGE_HEADER_SIZE);
        for (var e : queryParams.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append('&');
            }
            var key = e.getKey();
            var values = e.getValue();
            if (maskedQueryParams.contains(key.toLowerCase(Locale.ROOT))) {
                sb.append(key).append('=').append(mask);
            } else if (values.isEmpty()) {
                sb.append(key).append('=');
            } else {
                for (var value : values) {
                    if (!sb.isEmpty()) {
                        sb.append('&');
                    }
                    sb.append(key).append('=').append(value);
                }
            }
        }
        return sb.toString();
    }

    private boolean shouldWritePath() {
        return pathTemplate != null ? !pathTemplate : log.isTraceEnabled();
    }

    private String getOperation(String method, String path, String pathTemplate) {
        return method + ' ' + (shouldWritePath() ? path : pathTemplate);
    }
}
