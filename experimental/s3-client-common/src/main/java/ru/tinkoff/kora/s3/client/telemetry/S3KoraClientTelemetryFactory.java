package ru.tinkoff.kora.s3.client.telemetry;

import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface S3KoraClientTelemetryFactory {

    S3KoraClientTelemetry get(TelemetryConfig config, Class<?> clientImpl);
}
