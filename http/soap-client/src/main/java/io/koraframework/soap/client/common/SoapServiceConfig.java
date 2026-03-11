package io.koraframework.soap.client.common;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.telemetry.common.TelemetryConfig;

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
