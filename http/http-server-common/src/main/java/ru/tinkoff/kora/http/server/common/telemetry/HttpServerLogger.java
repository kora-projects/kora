package ru.tinkoff.kora.http.server.common.telemetry;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.http.common.HttpResultCode;
import ru.tinkoff.kora.http.server.common.HttpServer;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;

import javax.annotation.Nullable;

public interface HttpServerLogger {
    Logger log = LoggerFactory.getLogger(HttpServer.class);

    void logStart(String operation);

    void logEnd(String operation, Integer statusCode, HttpResultCode resultCode, long processingTime, @Nullable Throwable exception);

    final class DefaultHttpServerLogger implements HttpServerLogger {
        @Override
        public void logStart(String operation) {
            if (!log.isInfoEnabled()) {
                return;
            }
            log.info(StructuredArgument.marker("httpRequest", gen -> {
                gen.writeStartObject();
                gen.writeStringField("operation", operation);
                gen.writeEndObject();
            }), "Http request begin");
        }

        @Override
        public void logEnd(String operation, Integer statusCode, HttpResultCode resultCode, long processingTime, @Nullable Throwable exception) {
            if (!log.isInfoEnabled()) {
                return;
            }
            if (exception == null) {
                log.info(StructuredArgument.marker("httpResponse", gen -> {
                    gen.writeStartObject();
                    gen.writeStringField("operation", operation);
                    gen.writeNumberField("processingTime", processingTime / 1_000_000);
                    gen.writeNumberField("statusCode", statusCode);
                    gen.writeStringField("resultCode", resultCode.string());
                    gen.writeNullField("exceptionType");
                    gen.writeEndObject();
                }), "Http request end");

            } else {
                var exceptionType = exception.getClass().getCanonicalName();
                log.info(StructuredArgument.marker("httpResponse", gen -> {
                    gen.writeStartObject();
                    gen.writeStringField("operation", operation);
                    gen.writeNumberField("processingTime", processingTime / 1_000_000);
                    gen.writeNumberField("statusCode", statusCode);
                    gen.writeStringField("resultCode", resultCode.string());
                    gen.writeStringField("exceptionType", exceptionType);
                    gen.writeEndObject();
                }), "Http request end", exception);
            }
        }
    }
}
