package ru.tinkoff.kora.s3.client.telemetry;

import org.slf4j.LoggerFactory;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public class DefaultS3ClientLoggerFactory implements S3ClientLoggerFactory {

    @Override
    public S3ClientLogger get(TelemetryConfig.LogConfig logging, String clientName) {
        if (Objects.requireNonNullElse(logging.enabled(), false)) {
            var requestLog = LoggerFactory.getLogger(clientName + ".request");
            var responseLog = LoggerFactory.getLogger(clientName + ".response");
            return new DefaultS3ClientLogger(clientName, requestLog, responseLog);
        } else {
            return null;
        }
    }
}
