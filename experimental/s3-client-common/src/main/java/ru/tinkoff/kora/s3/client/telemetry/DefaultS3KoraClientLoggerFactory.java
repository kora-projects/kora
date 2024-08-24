package ru.tinkoff.kora.s3.client.telemetry;

import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.util.Objects;

public class DefaultS3KoraClientLoggerFactory implements S3KoraClientLoggerFactory {

    @Override
    public S3KoraClientLogger get(TelemetryConfig.LogConfig logging, Class<?> clientImpl) {
        if (Objects.requireNonNullElse(logging.enabled(), false)) {
            return new DefaultS3KoraClientLogger(clientImpl);
        } else {
            return null;
        }
    }
}
