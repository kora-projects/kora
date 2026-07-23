package ru.tinkoff.kora.soap.client.common;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.time.Duration;

@ConfigValueExtractor
public interface SoapServiceConfig {

    /**
     * @return Service URL where requests will be sent.
     */
    String url();

    /**
     * @return Maximum request execution time.
     */
    default Duration timeout() {
        return Duration.ofSeconds(60);
    }

    /**
     * @return Telemetry configuration of the SOAP client such as logging, metrics and tracing.
     */
    TelemetryConfig telemetry();
}
