package ru.tinkoff.kora.soap.client.common.telemetry;

import ru.tinkoff.kora.soap.client.common.SoapResult;

public interface SoapClientMetrics {

    void recordSuccess(SoapResult.Success result, long processingTime);

    void recordFailure(SoapClientTelemetry.SoapTelemetryContext.SoapClientFailure failure, long processingTime);
}
