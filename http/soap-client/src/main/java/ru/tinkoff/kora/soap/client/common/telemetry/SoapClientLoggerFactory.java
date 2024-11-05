package ru.tinkoff.kora.soap.client.common.telemetry;

import jakarta.annotation.Nullable;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

public interface SoapClientLoggerFactory {

    @Nullable
    SoapClientLogger get(TelemetryConfig.LogConfig logging, String serviceName, String soapMethod, String url);
}
