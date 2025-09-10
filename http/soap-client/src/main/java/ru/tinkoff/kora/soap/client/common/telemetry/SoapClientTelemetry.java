package ru.tinkoff.kora.soap.client.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.soap.client.common.SoapResult;
import ru.tinkoff.kora.soap.client.common.envelope.SoapEnvelope;

public interface SoapClientTelemetry {

    SoapTelemetryContext get(SoapEnvelope requestEnvelope);

    interface SoapTelemetryContext {

        boolean logResponseBody();

        void prepared(SoapEnvelope requestEnvelope, byte[] requestAsBytes);

        void success(SoapResult.Success success, @Nullable byte[] responseAsBytes);

        void failure(SoapClientFailure failure, @Nullable byte[] responseAsBytes);

        sealed interface SoapClientFailure {
            record InvalidHttpCode(int code) implements SoapClientFailure {}

            record InternalServerError(SoapResult.Failure result) implements SoapClientFailure {}

            record ProcessException(Throwable throwable) implements SoapClientFailure {}
        }
    }
}
