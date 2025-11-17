package ru.tinkoff.kora.soap.client.common.telemetry;

import ru.tinkoff.kora.common.telemetry.Observation;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.soap.client.common.SoapResult;
import ru.tinkoff.kora.soap.client.common.envelope.SoapEnvelope;

public interface SoapClientObservation extends Observation {
    void observeRequest(SoapEnvelope requestEnvelope);

    void observeRequestXml(byte[] requestXml);

    void observeHttpResponse(HttpClientResponse httpClientResponse);

    void observeResponseBody(byte[] body);

    void observeFailure(SoapResult.Failure result);

    void observeResult(Object body);

}
