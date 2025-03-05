package ru.tinkoff.kora.s3.client.telemetry;

import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public class DefaultS3LoggerFactory implements S3LoggerFactory {
    public DefaultS3LoggerFactory() {
    }

    @Override
    public S3Logger get(TelemetryConfig.LogConfig logging, Class<?> client) {
        if (Objects.requireNonNullElse(logging.enabled(), false)) {
            return new DefaultS3Logger(client);
        } else {
            return null;
        }
    }
}
