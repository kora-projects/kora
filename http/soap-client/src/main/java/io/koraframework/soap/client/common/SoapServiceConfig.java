package io.koraframework.soap.client.common;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.soap.client.common.telemetry.SoapClientTelemetryConfig;

import java.time.Duration;

@ConfigMapper
public interface SoapServiceConfig {

    String url();

    default Duration timeout() {
        return Duration.ofSeconds(60);
    }

    SoapClientTelemetryConfig telemetry();
}
