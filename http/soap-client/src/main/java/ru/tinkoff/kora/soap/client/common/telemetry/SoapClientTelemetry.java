package ru.tinkoff.kora.soap.client.common.telemetry;

import ru.tinkoff.kora.soap.client.common.envelope.SoapEnvelope;

public interface SoapClientTelemetry {

    SoapClientObservation observe(SoapEnvelope requestEnvelope);
}
