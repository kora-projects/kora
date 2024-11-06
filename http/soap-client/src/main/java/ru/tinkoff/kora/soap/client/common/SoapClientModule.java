package ru.tinkoff.kora.soap.client.common;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.common.DefaultComponent;
import ru.tinkoff.kora.soap.client.common.telemetry.*;

import java.nio.charset.StandardCharsets;

public interface SoapClientModule {

    @DefaultComponent
    default SoapClientLogger.SoapClientLoggerBodyMapper defaultSoapClientLoggerBodyMapper() {
        return new SoapClientLogger.SoapClientLoggerBodyMapper() {
            @Override
            public String mapRequest(byte[] requestAsBytes) {
                return new String(requestAsBytes, StandardCharsets.UTF_8);
            }

            @Override
            public String mapResponseSuccess(byte[] responseAsBytes) {
                return new String(responseAsBytes, StandardCharsets.UTF_8);
            }

            @Override
            public String mapResponseFailure(byte[] responseAsBytes) {
                return new String(responseAsBytes, StandardCharsets.UTF_8);
            }
        };
    }

    @DefaultComponent
    default SoapClientLoggerFactory defaultSoapClientLoggerFactory(SoapClientLogger.SoapClientLoggerBodyMapper mapper) {
        return new Sl4fjSoapClientLoggerFactory(mapper);
    }

    @DefaultComponent
    default DefaultSoapClientTelemetryFactory defaultSoapClientTelemetryFactory(@Nullable SoapClientLoggerFactory loggerFactory,
                                                                                @Nullable SoapClientMetricsFactory metricsFactory,
                                                                                @Nullable SoapClientTracerFactory tracerFactory) {
        return new DefaultSoapClientTelemetryFactory(loggerFactory, metricsFactory, tracerFactory);
    }
}
