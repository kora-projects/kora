package ru.tinkoff.kora.telemetry.common;

import ru.tinkoff.kora.config.common.annotation.ConfigValueExtractor;

import java.time.Duration;
import java.util.Map;

@ConfigValueExtractor
public interface TelemetryConfig {
    LogConfig logging();

    TracingConfig tracing();

    MetricsConfig metrics();

    @ConfigValueExtractor
    interface LogConfig {
        default boolean enabled() {
            return false;
        }
    }

    @ConfigValueExtractor
    interface TracingConfig {
        default boolean enabled() {
            return true;
        }

        default Map<String, String> attributes() {
            return Map.of();
        }
    }

    @ConfigValueExtractor
    interface MetricsConfig {
        Duration[] DEFAULT_SLO = new Duration[]{
            Duration.ofMillis(1),
            Duration.ofMillis(10),
            Duration.ofMillis(50),
            Duration.ofMillis(100),
            Duration.ofMillis(200),
            Duration.ofMillis(500),
            Duration.ofMillis(1000),
            Duration.ofMillis(2000),
            Duration.ofMillis(5000),
            Duration.ofMillis(10000),
            Duration.ofMillis(20000),
            Duration.ofMillis(30000),
            Duration.ofMillis(60000),
            Duration.ofMillis(90000)
        };

        default boolean enabled() {
            return false;
        }

        default Duration[] slo() {
            return DEFAULT_SLO;
        }

        default Map<String, String> tags() {
            return Map.of();
        }
    }
}
