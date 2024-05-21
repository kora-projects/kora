package ru.tinkoff.kora.camunda.rest;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;
import ru.tinkoff.kora.telemetry.common.TelemetryConfig;

import java.time.Duration;

@ConfigValueExtractor
public interface CamundaRestConfig {

    default boolean enabled() {
        return false;
    }

    default String path() {
        return "/engine-rest";
    }

    default Integer port() {
        return 8090;
    }

    default Duration shutdownWait() {
        return Duration.ofMillis(100);
    }

    CamundaRestTelemetryConfig telemetry();

    @ConfigValueExtractor
    interface CamundaRestTelemetryConfig extends TelemetryConfig {

        @Override
        CamundaRestLogConfig logging();
    }

    @ConfigValueExtractor
    interface CamundaRestLogConfig extends TelemetryConfig.LogConfig {

        default boolean stacktrace() {
            return true;
        }
    }
}
