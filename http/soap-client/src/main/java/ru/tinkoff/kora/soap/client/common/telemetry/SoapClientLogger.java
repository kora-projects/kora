package ru.tinkoff.kora.soap.client.common.telemetry;

import ru.tinkoff.kora.soap.client.common.SoapResult;
import ru.tinkoff.kora.soap.client.common.envelope.SoapEnvelope;

public interface SoapClientLogger {

    void logRequest(SoapEnvelope requestEnvelope);

    void logSuccess(SoapResult.Success result);

    void logFailure(SoapClientTelemetry.SoapTelemetryContext.SoapClientFailure failure);
}
