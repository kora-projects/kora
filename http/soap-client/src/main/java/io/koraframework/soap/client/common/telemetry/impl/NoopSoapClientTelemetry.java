package io.koraframework.soap.client.common.telemetry.impl;

import io.koraframework.soap.client.common.envelope.SoapEnvelope;
import io.koraframework.soap.client.common.telemetry.SoapClientObservation;
import io.koraframework.soap.client.common.telemetry.SoapClientTelemetry;

public final class NoopSoapClientTelemetry implements SoapClientTelemetry {

    public static final NoopSoapClientTelemetry INSTANCE = new NoopSoapClientTelemetry();

    private NoopSoapClientTelemetry() { }

    @Override
    public SoapClientObservation observe(SoapEnvelope requestEnvelope) {
        return NoopSoapClientObservation.INSTANCE;
    }
}
