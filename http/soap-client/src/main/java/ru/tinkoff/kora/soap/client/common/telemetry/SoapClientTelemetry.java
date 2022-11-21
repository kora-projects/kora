package ru.tinkoff.kora.soap.client.common.telemetry;

import ru.tinkoff.kora.soap.client.common.SoapResult;
import ru.tinkoff.kora.soap.client.common.envelope.SoapEnvelope;

public interface SoapClientTelemetry {
    SoapTelemetryContext get(SoapEnvelope requestEnvelope);

    interface SoapTelemetryContext {
        void success(SoapResult.Success result);

        void failure(SoapClientFailure failure);

        sealed interface SoapClientFailure {
            record InvalidHttpCode(int code) implements SoapClientFailure {}
            record InternalServerError(SoapResult.Failure result) implements SoapClientFailure {}
            record ProcessException(Throwable throwable) implements SoapClientFailure {}
        }
    }
}
