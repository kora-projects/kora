package ru.tinkoff.kora.soap.client.common.telemetry;

import ru.tinkoff.kora.soap.client.common.envelope.SoapEnvelope;

public class NoopSoapClientTelemetry implements SoapClientTelemetry {
    public static final NoopSoapClientTelemetry INSTANCE = new NoopSoapClientTelemetry();

    @Override
    public SoapClientObservation observe(SoapEnvelope requestEnvelope) {
        return NoopSoapClientObservation.INSTANCE;
    }
}
