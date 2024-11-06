package ru.tinkoff.kora.soap.client.common.telemetry;

import ru.tinkoff.kora.common.Context;
import ru.tinkoff.kora.http.client.common.request.HttpClientRequest;
import ru.tinkoff.kora.soap.client.common.SoapResult;
import ru.tinkoff.kora.soap.client.common.envelope.SoapEnvelope;

public interface SoapClientTracer {

    interface SoapClientSpan {

        void success(SoapResult.Success result);

        void failure(SoapClientTelemetry.SoapTelemetryContext.SoapClientFailure failure);
    }

    SoapClientSpan createSpan(Context ctx, SoapEnvelope requestEnvelope);
}
