package ru.tinkoff.kora.soap.client.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface SoapClientTracerFactory {

    @Nullable
    SoapClientTracer get(TelemetryConfig.TracingConfig config, String serviceClass, String serviceName, String soapMethod, String url);
}
