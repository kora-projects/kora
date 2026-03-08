package io.koraframework.soap.client.common.telemetry;

import io.koraframework.common.telemetry.Observation;
import io.koraframework.http.client.common.response.HttpClientResponse;
import io.koraframework.soap.client.common.SoapResult;
import io.koraframework.soap.client.common.envelope.SoapEnvelope;

public interface SoapClientObservation extends Observation {
    void observeRequest(SoapEnvelope requestEnvelope);

    void observeRequestXml(byte[] requestXml);

    void observeHttpResponse(HttpClientResponse httpClientResponse);

    void observeResponseBody(byte[] body);

    void observeFailure(SoapResult.Failure result);

    void observeResult(Object body);

}
