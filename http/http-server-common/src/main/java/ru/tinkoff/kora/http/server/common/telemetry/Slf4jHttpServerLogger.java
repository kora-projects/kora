package ru.tinkoff.kora.http.server.common.telemetry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.http.common.HttpHeaders;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.server.common.HttpServer;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;

import javax.annotation.Nullable;

public final class Slf4jHttpServerLogger implements HttpServerLogger {

    private static final Logger log = LoggerFactory.getLogger(HttpServer.class);

    @Override
    public boolean isEnabled() {
        return log.isInfoEnabled();
    }

    @Override
    public void logStart(String operation, @Nullable HttpHeaders headers) {
        if (!log.isInfoEnabled()) {
            return;
        }

        var marker = StructuredArgument.marker("httpRequest", gen -> {
            gen.writeStartObject();
            gen.writeStringField("operation", operation);
            gen.writeEndObject();
        });

        if (log.isDebugEnabled() && headers != null && headers.size() > 0) {
            var headersString = HttpHeaders.toString(headers);
            log.debug(marker, "HttpRequest received for {}\n{}", operation, headersString);
        } else {
            log.info(marker, "HttpRequest received for {}", operation);
        }
    }

    @Override
    public void logEnd(String operation,
                       Integer statusCode,
                       HttpResultCode resultCode,
                       long processingTime,
                       @Nullable HttpHeaders headers,
                       @Nullable Throwable exception) {
        if (!log.isInfoEnabled()) {
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

        if (log.isDebugEnabled() && headers != null && headers.size() > 0) {
            var headersString = HttpHeaders.toString(headers);
            log.debug(marker, "HttpRequest responded {} for {}\n{}", statusCode, operation, headersString);
        } else if (statusCode != null) {
            log.info(marker, "HttpRequest responded {} for {}", statusCode, operation);
        }
    }
}
