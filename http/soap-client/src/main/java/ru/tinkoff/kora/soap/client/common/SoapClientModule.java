package ru.tinkoff.kora.soap.client.common;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.soap.client.common.telemetry.*;

public interface SoapClientModule {

    @DefaultComponent
    default SoapClientLoggerFactory defaultSoapClientLoggerFactory() {
        return new Sl4fjSoapClientLoggerFactory();
    }

    @DefaultComponent
    default DefaultSoapClientTelemetryFactory defaultSoapClientTelemetryFactory(@Nullable SoapClientLoggerFactory loggerFactory,
                                                                                @Nullable SoapClientMetricsFactory metricsFactory,
                                                                                @Nullable SoapClientTracerFactory tracerFactory) {
        return new DefaultSoapClientTelemetryFactory(loggerFactory, metricsFactory, tracerFactory);
    }
}
