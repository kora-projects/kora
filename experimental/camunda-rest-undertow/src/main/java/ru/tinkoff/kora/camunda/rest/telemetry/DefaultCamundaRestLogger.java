package ru.tinkoff.kora.camunda.rest.telemetry;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.camunda.rest.CamundaRest;
import ru.tinkoff.kora.logging.common.arg.StructuredArgument;

public final class DefaultCamundaRestLogger implements CamundaRestLogger {

    private static final Logger logger = LoggerFactory.getLogger(CamundaRest.class);

    private final boolean logStacktrace;

    public DefaultCamundaRestLogger(boolean stacktrace) {
        this.logStacktrace = stacktrace;
    }

    @Override
    public void logStart(String method, String path) {
        if (!logger.isInfoEnabled()) {
            return;
        }

        var marker = StructuredArgument.marker("httpRequest", gen -> {
            gen.writeStartObject();
            gen.writeStringField("operation", method);
            gen.writeEndObject();
        });

        logger.info(marker, "Camunda RestRequest received for {} {}", method, path);
    }

    @Override
    public void logEnd(String method,
                       String path,
                       int statusCode,
                       long processingTime,
                       @Nullable Throwable exception) {
        if (!logger.isWarnEnabled()) {
            return;
        }

        var marker = StructuredArgument.marker("httpResponse", gen -> {
            gen.writeStartObject();
            gen.writeStringField("operation", method);
            gen.writeNumberField("processingTime", processingTime / 1_000_000);
            gen.writeNumberField("statusCode", statusCode);
            if (exception != null) {
                var exceptionType = exception.getClass().getCanonicalName();
                gen.writeStringField("exceptionType", exceptionType);
            }
            gen.writeEndObject();
        });

        if (statusCode != -1) {
            if (this.logStacktrace && exception != null) {
                logger.warn(marker, "Camunda RestRequest failed with status code {} for {} {}", statusCode, method, path, exception);
            } else if (exception != null) {
                logger.warn(marker, "Camunda RestRequest failed with status code {} for {} {} with message: {}", statusCode, method, path, exception.getMessage());
            } else if (statusCode >= 400) {
                logger.warn(marker, "Camunda RestRequest failed with status code {} for {} {}", statusCode, method, path);
            } else {
                logger.info(marker, "Camunda RestRequest responded with status code {} for {} {}", statusCode, method, path);
            }
        } else {
            if (this.logStacktrace && exception != null) {
                logger.warn(marker, "Camunda RestRequest processing error for {}", method, exception);
            } else if (exception != null) {
                logger.warn(marker, "Camunda RestRequest processing error for {} with message {}", statusCode, exception.getMessage());
            } else {
                logger.warn(marker, "Camunda RestRequest processing error for {}", method);
            }
        }
    }
}
