package ru.tinkoff.kora.s3.client.telemetry;

import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface S3ClientTelemetryFactory {

    S3ClientTelemetry get(TelemetryConfig config, String clientName);
}
