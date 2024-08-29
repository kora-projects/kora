package ru.tinkoff.kora.s3.client.telemetry;

import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public class DefaultS3ClientLoggerFactory implements S3ClientLoggerFactory {

    @Override
    public S3ClientLogger get(TelemetryConfig.LogConfig logging, Class<?> client) {
        if (Objects.requireNonNullElse(logging.enabled(), false)) {
            return new DefaultS3ClientLogger(client);
        } else {
            return null;
        }
    }
}
