package ru.tinkoff.kora.soap.client.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.soap.client.common.SoapResult;
import ru.tinkoff.kora.soap.client.common.envelope.SoapEnvelope;

public interface SoapClientLogger {

    boolean logResponseBody();

    void logRequest(SoapEnvelope requestEnvelope,
                    byte[] requestAsBytes);

    void logSuccess(SoapResult.Success result,
                    @Nullable byte[] responseAsBytes);

    void logFailure(SoapClientTelemetry.SoapTelemetryContext.SoapClientFailure failure,
                    @Nullable byte[] responseAsBytes);

    interface SoapClientLoggerBodyMapper {

        String mapRequest(byte[] requestAsBytes);

        String mapResponseSuccess(byte[] responseAsBytes);

        String mapResponseFailure(byte[] responseAsBytes);
    }
}
