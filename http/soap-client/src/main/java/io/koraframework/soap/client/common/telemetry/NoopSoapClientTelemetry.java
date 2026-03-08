package io.koraframework.soap.client.common.telemetry;

import io.koraframework.soap.client.common.envelope.SoapEnvelope;

public class NoopSoapClientTelemetry implements SoapClientTelemetry {
    public static final NoopSoapClientTelemetry INSTANCE = new NoopSoapClientTelemetry();

    @Override
    public SoapClientObservation observe(SoapEnvelope requestEnvelope) {
        return NoopSoapClientObservation.INSTANCE;
    }
}
