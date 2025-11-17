package ru.tinkoff.kora.soap.client.common;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.time.Duration;

@ConfigValueExtractor
public interface SoapServiceConfig {

    String url();

    default Duration timeout() {
        return Duration.ofSeconds(60);
    }

    SoapClientTelemetryConfig telemetry();

    @ConfigValueExtractor
    interface SoapClientTelemetryConfig extends TelemetryConfig {

    }
}
