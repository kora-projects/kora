package ru.tinkoff.kora.soap.client.common.telemetry;

import ru.tinkoff.kora.soap.client.common.SoapMethodDescriptor;
import ru.tinkoff.kora.soap.client.common.SoapServiceConfig;

public interface SoapClientTelemetryFactory {

    SoapClientTelemetry get(SoapServiceConfig.SoapClientTelemetryConfig config, SoapMethodDescriptor descriptor, String url);
}
