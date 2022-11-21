package ru.tinkoff.kora.soap.client.common;

import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.soap.client.common.telemetry.DefaultSoapClientTelemetryFactory;
import ru.tinkoff.kora.soap.client.common.telemetry.SoapClientMetricsFactory;

import javax.annotation.Nullable;

public interface SoapClientModule {
    @DefaultComponent
    default DefaultSoapClientTelemetryFactory defaultSoapClientTelemetryFactory(@Nullable SoapClientMetricsFactory metricsFactory) {
        return new DefaultSoapClientTelemetryFactory(metricsFactory);
    }
}
