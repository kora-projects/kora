package ru.tinkoff.kora.http.server.common.telemetry;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.common.header.HttpHeaders;
import ru.tinkoff.kora.http.server.common.HttpServer;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;

public final class Slf4jHttpServerLogger implements HttpServerLogger {

    private static final Logger log = LoggerFactory.getLogger(HttpServer.class);
    private final boolean logStacktrace;

    public Slf4jHttpServerLogger(boolean stacktrace) {
        this.logStacktrace = stacktrace;
    }

    @Override
    public boolean isEnabled() {
        return log.isInfoEnabled();
    }

    @Override
    public void logStart(String method,
                         String operation,
                         String path,
                         @Nullable HttpHeaders headers) {
        if (!log.isInfoEnabled()) {
            return;
        }

        var marker = StructuredArgument.marker("httpRequest", gen -> {
            gen.writeStartObject();
            gen.writeStringField("operation", operation);
            gen.writeEndObject();
        });

        if (log.isDebugEnabled() && headers != null && !headers.isEmpty()) {
            var headersString = HttpHeaders.toString(headers);
            log.debug(marker, "HttpServer received for {} {}\n{}", method, path, headersString);
        } else {
            log.info(marker, "HttpServer received for {}", operation);
        }
    }

    @Override
    public void logEnd(String method,
                       String operation,
                       String path,
                       Integer statusCode,
                       HttpResultCode resultCode,
                       long processingTime,
                       @Nullable HttpHeaders headers,
                       @Nullable Throwable exception) {
        if (!log.isWarnEnabled()) {
            return;
        }

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

        if (log.isDebugEnabled() && headers != null && !headers.isEmpty()) {
            var headersString = HttpHeaders.toString(headers);
            if (exception != null) {
                if (this.logStacktrace) {
                    log.warn(marker, "HttpServer processing error {} for {} {}\n{}", statusCode, method, path, headersString, exception);
                } else {
                    log.warn(marker, "HttpServer processing error {} for {} {} due to: {} \n{}", statusCode, method, path, exception.getMessage(), headersString);
                }
            } else {
                log.debug(marker, "HttpServer responded {} for {} {}\n{}", statusCode, method, path, headersString);
            }
        } else if (statusCode != null) {
            if (exception != null) {
                if (this.logStacktrace) {
                    log.warn(marker, "HttpServer processing error {} for {}", statusCode, operation, exception);
                } else {
                    log.warn(marker, "HttpServer processing error {} for {} due to: {}", statusCode, operation, exception.getMessage());
                }
            } else {
                log.info(marker, "HttpServer responded {} for {}", statusCode, operation);
            }
        } else {
            if (exception != null) {
                if (this.logStacktrace) {
                    log.warn(marker, "HttpServer processing error for {}", operation, exception);
                } else {
                    log.warn(marker, "HttpServer processing error for {} due to: {}", operation, exception.getMessage());
                }
            } else {
                log.info(marker, "HttpServer responded for {}", operation);
            }
        }
    }
}
