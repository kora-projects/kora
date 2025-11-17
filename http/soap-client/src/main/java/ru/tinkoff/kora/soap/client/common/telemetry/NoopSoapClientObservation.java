package ru.tinkoff.kora.soap.client.common.telemetry;

import io.opentelemetry.api.trace.Span;
import ru.tinkoff.kora.http.client.common.response.HttpClientResponse;
import ru.tinkoff.kora.soap.client.common.SoapResult;
import ru.tinkoff.kora.soap.client.common.envelope.SoapEnvelope;

public class NoopSoapClientObservation implements SoapClientObservation {
    public static final NoopSoapClientObservation INSTANCE = new NoopSoapClientObservation();

    @Override
    public void observeRequest(SoapEnvelope requestEnvelope) {

    }

    @Override
    public void observeRequestXml(byte[] requestXml) {

    }

    @Override
    public void observeHttpResponse(HttpClientResponse httpClientResponse) {

    }

    @Override
    public void observeResponseBody(byte[] body) {

    }

    @Override
    public void observeFailure(SoapResult.Failure result) {

    }

    @Override
    public void observeResult(Object body) {

    }

    @Override
    public Span span() {
        return Span.getInvalid();
    }

    @Override
    public void end() {

    }

    @Override
    public void observeError(Throwable e) {

    }
}
