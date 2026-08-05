package io.koraframework.soap.client.common;

import io.koraframework.config.common.annotation.ConfigMapper;
import io.koraframework.soap.client.common.telemetry.SoapClientTelemetryConfig;

import java.time.Duration;

@ConfigMapper
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
    SoapClientTelemetryConfig telemetry();
}
