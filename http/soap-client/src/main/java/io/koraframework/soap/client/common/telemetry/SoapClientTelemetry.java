package io.koraframework.soap.client.common.telemetry;

import io.koraframework.soap.client.common.envelope.SoapEnvelope;

public interface SoapClientTelemetry {

    SoapClientObservation observe(SoapEnvelope requestEnvelope);
}
