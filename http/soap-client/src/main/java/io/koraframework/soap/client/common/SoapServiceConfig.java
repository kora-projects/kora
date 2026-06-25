package io.koraframework.soap.client.common;

import io.koraframework.config.common.annotation.ConfigValueExtractor;
import io.koraframework.soap.client.common.telemetry.SoapClientTelemetryConfig;

import java.time.Duration;

@ConfigValueExtractor
public interface SoapServiceConfig {

    String url();

    default Duration timeout() {
        return Duration.ofSeconds(60);
    }

    SoapClientTelemetryConfig telemetry();
}
